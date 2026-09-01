//! HyperOS (Joyose) cloud-config document logic.
//!
//! Pure layer: `&serde_json::Value` in, structured results out. No DB, no
//! filesystem, no shell — unit-testable without a device.

pub mod appview;
pub mod caps;
pub mod resolve;
pub mod scoped;
pub mod store;
