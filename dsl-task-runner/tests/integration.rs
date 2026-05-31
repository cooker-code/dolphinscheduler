use dsl_task_runner::proto::dsl_runner::v1::{
    task_event::Payload, task_runner_client::TaskRunnerClient, CancelRequest, TaskRequest,
};
use hyper_util::rt::TokioIo;
use std::path::PathBuf;
use tokio::net::UnixStream;
use tonic::transport::{Channel, Endpoint};
use tower::service_fn;

// ── helpers ──────────────────────────────────────────────────────────────────

/// Spawn a server on the given unix-socket path and wait until it is ready.
async fn start_test_server(suffix: &str) -> PathBuf {
    use dsl_task_runner::{
        proto::dsl_runner::v1::task_runner_server::TaskRunnerServer, runner::TaskRunnerService,
    };
    use tokio::net::UnixListener;
    use tokio_stream::wrappers::UnixListenerStream;
    use tonic::transport::Server;

    let sock = PathBuf::from(format!("/tmp/dsl-runner-test-{}.sock", suffix));

    // Clean up stale socket from a previous (failed) run.
    if sock.exists() {
        std::fs::remove_file(&sock).ok();
    }

    let sock_for_server = sock.clone();
    tokio::spawn(async move {
        let listener = UnixListener::bind(&sock_for_server).expect("bind unix socket");
        let incoming = UnixListenerStream::new(listener);
        Server::builder()
            .add_service(TaskRunnerServer::new(TaskRunnerService::new()))
            .serve_with_incoming(incoming)
            .await
            .expect("server error");
    });

    // Poll until the socket file actually exists (server is ready to accept).
    for _ in 0..50 {
        if sock.exists() {
            break;
        }
        tokio::time::sleep(std::time::Duration::from_millis(20)).await;
    }

    sock
}

/// Build a gRPC client that connects over the unix socket.
async fn make_client(sock: &PathBuf) -> TaskRunnerClient<Channel> {
    let sock_path = sock.clone();
    let channel = Endpoint::try_from("http://[::]:50051")
        .unwrap()
        .connect_with_connector(service_fn(move |_: tonic::transport::Uri| {
            let path = sock_path.clone();
            async move {
                let stream = UnixStream::connect(path).await?;
                Ok::<_, std::io::Error>(TokioIo::new(stream))
            }
        }))
        .await
        .expect("connect to unix socket");

    TaskRunnerClient::new(channel)
}

// ── tests ─────────────────────────────────────────────────────────────────────

#[tokio::test]
async fn shell_exec_exit_zero() {
    let sock = start_test_server("exit-zero").await;
    let mut client = make_client(&sock).await;

    let req = TaskRequest {
        task_instance_id: "t-zero".to_string(),
        task_type: "SHELL".to_string(),
        script: "echo hello".to_string(),
        working_dir: "/tmp".to_string(),
        ..Default::default()
    };

    let mut stream = client.execute(req).await.expect("execute rpc").into_inner();

    let mut exit_code: Option<i32> = None;
    while let Some(event) = stream.message().await.expect("stream error") {
        if let Some(Payload::ExitResult(r)) = event.payload {
            exit_code = Some(r.exit_code);
        }
    }

    assert_eq!(exit_code, Some(0), "expected exit_code == 0");
}

#[tokio::test]
async fn shell_exec_exit_nonzero() {
    let sock = start_test_server("exit-nonzero").await;
    let mut client = make_client(&sock).await;

    let req = TaskRequest {
        task_instance_id: "t-nonzero".to_string(),
        task_type: "SHELL".to_string(),
        script: "exit 1".to_string(),
        working_dir: "/tmp".to_string(),
        ..Default::default()
    };

    let mut stream = client.execute(req).await.expect("execute rpc").into_inner();

    let mut exit_code: Option<i32> = None;
    while let Some(event) = stream.message().await.expect("stream error") {
        if let Some(Payload::ExitResult(r)) = event.payload {
            exit_code = Some(r.exit_code);
        }
    }

    assert_eq!(exit_code, Some(1), "expected exit_code == 1");
}

#[tokio::test]
async fn shell_exec_cancel() {
    let sock = start_test_server("cancel").await;
    let mut client = make_client(&sock).await;
    let mut cancel_client = make_client(&sock).await;

    let req = TaskRequest {
        task_instance_id: "t-cancel".to_string(),
        task_type: "SHELL".to_string(),
        script: "sleep 60".to_string(),
        working_dir: "/tmp".to_string(),
        ..Default::default()
    };

    let mut stream = client.execute(req).await.expect("execute rpc").into_inner();

    // Wait 200 ms then send Cancel.
    tokio::time::sleep(std::time::Duration::from_millis(200)).await;

    cancel_client
        .cancel(CancelRequest {
            task_instance_id: "t-cancel".to_string(),
        })
        .await
        .expect("cancel rpc");

    // Drain the stream to get ExitResult.
    let mut exit_code: Option<i32> = None;
    while let Some(event) = stream.message().await.expect("stream error") {
        if let Some(Payload::ExitResult(r)) = event.payload {
            exit_code = Some(r.exit_code);
        }
    }

    assert_ne!(
        exit_code,
        Some(0),
        "expected non-zero exit after cancel, got {:?}",
        exit_code
    );
}
