use tokio::sync::mpsc;
use tokio_stream::wrappers::ReceiverStream;
use tonic::{async_trait, Request, Response, Status};

use crate::cancel::CancelRegistry;
use crate::executor::spawn_shell;
use crate::proto::dsl_runner::v1::{
    task_runner_server::TaskRunner, CancelRequest, CancelResponse, TaskEvent, TaskRequest,
};

pub struct TaskRunnerService {
    cancel_registry: CancelRegistry,
}

impl TaskRunnerService {
    pub fn new() -> Self {
        Self {
            cancel_registry: CancelRegistry::new(),
        }
    }
}

impl Default for TaskRunnerService {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl TaskRunner for TaskRunnerService {
    type ExecuteStream = ReceiverStream<Result<TaskEvent, Status>>;

    async fn execute(
        &self,
        request: Request<TaskRequest>,
    ) -> Result<Response<Self::ExecuteStream>, Status> {
        let req = request.into_inner();
        let task_id = req.task_instance_id.clone();

        let token = self.cancel_registry.register(&task_id);
        let (tx, rx) = mpsc::channel(64);

        let registry = self.cancel_registry.clone();
        tokio::spawn(async move {
            spawn_shell(req, tx, token).await;
            registry.remove(&task_id);
        });

        Ok(Response::new(ReceiverStream::new(rx)))
    }

    async fn cancel(
        &self,
        request: Request<CancelRequest>,
    ) -> Result<Response<CancelResponse>, Status> {
        let id = request.into_inner().task_instance_id;
        let ok = self.cancel_registry.cancel(&id);
        Ok(Response::new(CancelResponse { ok }))
    }
}
