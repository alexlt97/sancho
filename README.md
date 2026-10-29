# Sancho

Sancho is an offline-first Android project for running local language and vision models directly on a phone.

The initial target is a Pixel 9a running GrapheneOS. The project is designed around privacy, local processing, and user control: model files are provided explicitly, inference happens on the device, and the application does not depend on cloud services for its core experience.

The repository is terminal-first. Its development and verification workflow is intended to work without Android Studio, using reproducible tooling, explicit checks, and documented constraints.

The project will evolve in small, reviewable steps. The first milestone is a reliable local chat foundation; agent actions, phone tools, and broader automation will be considered only after that foundation is stable.

See [Version 1 scope and development notes](docs/v1.md) for the current implementation details, terminal workflow, and known boundaries.

See [Version 2 planning](docs/v2.md) for the future tool-calling, internet, terminal, SSH, and phone-action roadmap.
