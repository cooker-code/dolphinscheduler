use std::os::unix::fs::PermissionsExt;
use std::time::Duration;

use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;
use tokio::sync::mpsc::Sender;
use tokio::time::timeout;
use tokio_util::sync::CancellationToken;
use tonic::Status;

use crate::proto::dsl_runner::v1::{
    task_event::Payload, ExitResult, LogLine, TaskEvent, TaskRequest,
};

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}

pub async fn spawn_shell(
    req: TaskRequest,
    tx: Sender<Result<TaskEvent, Status>>,
    token: CancellationToken,
) {
    // a. Write temp script
    let script_path = format!("{}/{}.sh", req.working_dir, req.task_instance_id);
    if let Err(e) = tokio::fs::write(&script_path, req.script.as_bytes()).await {
        let _ = tx
            .send(Err(Status::internal(format!(
                "failed to write script: {e}"
            ))))
            .await;
        return;
    }
    if let Err(e) =
        tokio::fs::set_permissions(&script_path, std::fs::Permissions::from_mode(0o700)).await
    {
        let _ = tx
            .send(Err(Status::internal(format!(
                "failed to chmod script: {e}"
            ))))
            .await;
        return;
    }

    // b. Spawn child process
    // process_group(0) makes the child its own process group leader so that
    // a kill(-pgid, SIGKILL) can reach all sub-processes (e.g. `sleep 60`
    // spawned by bash).
    let mut child = match Command::new("bash")
        .arg(&script_path)
        .envs(req.env.iter())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .process_group(0)
        .spawn()
    {
        Ok(c) => c,
        Err(e) => {
            let _ = tx
                .send(Err(Status::internal(format!("failed to spawn: {e}"))))
                .await;
            let _ = tokio::fs::remove_file(&script_path).await;
            return;
        }
    };

    // c. Get pid
    let pid = child.id().unwrap_or(0) as i32;

    // d. Stream stdout and stderr
    let stdout = child.stdout.take().expect("stdout was piped");
    let stderr = child.stderr.take().expect("stderr was piped");

    let tx_out = tx.clone();
    let stdout_task = tokio::spawn(async move {
        let mut lines = BufReader::new(stdout).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            let event = TaskEvent {
                payload: Some(Payload::LogLine(LogLine {
                    timestamp_ms: now_ms(),
                    line,
                })),
            };
            if tx_out.send(Ok(event)).await.is_err() {
                break;
            }
        }
    });

    let tx_err = tx.clone();
    let stderr_task = tokio::spawn(async move {
        let mut lines = BufReader::new(stderr).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            let event = TaskEvent {
                payload: Some(Payload::LogLine(LogLine {
                    timestamp_ms: now_ms(),
                    line,
                })),
            };
            if tx_err.send(Ok(event)).await.is_err() {
                break;
            }
        }
    });

    // e. Wait with cancellation and timeout
    let timeout_secs = if req.timeout_seconds > 0 {
        req.timeout_seconds as u64
    } else {
        3600
    };

    let exit_code: i32 = tokio::select! {
        result = child.wait() => {
            match result {
                Ok(status) => status.code().unwrap_or(-1),
                Err(_) => -1,
            }
        }
        _ = token.cancelled() => {
            let _ = child.kill().await;
            -2
        }
        _ = timeout(Duration::from_secs(timeout_secs), std::future::pending::<()>()) => {
            let _ = child.kill().await;
            -3
        }
    };

    // Abort reader tasks to avoid blocking on pipes held by orphaned sub-processes.
    stdout_task.abort();
    stderr_task.abort();
    let _ = stdout_task.await;
    let _ = stderr_task.await;

    // f. Delete temp script and send exit result
    let _ = tokio::fs::remove_file(&script_path).await;

    let exit_event = TaskEvent {
        payload: Some(Payload::ExitResult(ExitResult {
            exit_code,
            process_id: pid,
        })),
    };
    let _ = tx.send(Ok(exit_event)).await;
}
