use dashmap::DashMap;
use std::sync::Arc;
use tokio_util::sync::CancellationToken;

#[derive(Clone, Default)]
pub struct CancelRegistry {
    map: Arc<DashMap<String, CancellationToken>>,
}

impl CancelRegistry {
    pub fn new() -> Self {
        Self {
            map: Arc::new(DashMap::new()),
        }
    }

    pub fn register(&self, id: &str) -> CancellationToken {
        let token = CancellationToken::new();
        self.map.insert(id.to_string(), token.clone());
        token
    }

    pub fn cancel(&self, id: &str) -> bool {
        if let Some(token) = self.map.get(id) {
            token.cancel();
            true
        } else {
            false
        }
    }

    pub fn remove(&self, id: &str) {
        self.map.remove(id);
    }
}
