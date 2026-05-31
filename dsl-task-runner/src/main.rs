use dsl_task_runner::{
    cancel::CancelRegistry, proto::dsl_runner::v1::task_runner_server::TaskRunnerServer,
    runner::TaskRunnerService,
};
use tokio::net::UnixListener;
use tokio_stream::wrappers::UnixListenerStream;
use tonic::transport::Server;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::from_default_env()
                .add_directive(tracing::Level::INFO.into()),
        )
        .init();

    let sock_path = std::env::args()
        .nth(1)
        .unwrap_or_else(|| "/tmp/dsl-runner-50053.sock".to_string());

    // Remove stale socket file
    if std::path::Path::new(&sock_path).exists() {
        std::fs::remove_file(&sock_path)?;
    }

    let listener = UnixListener::bind(&sock_path)?;
    let incoming = UnixListenerStream::new(listener);

    tracing::info!("dsl-task-runner listening on unix:{}", sock_path);

    // CancelRegistry is embedded in the service; the variable is used via TaskRunnerService
    let _registry = CancelRegistry::new();
    let service = TaskRunnerService::new();

    Server::builder()
        .add_service(TaskRunnerServer::new(service))
        .serve_with_incoming(incoming)
        .await?;

    Ok(())
}
