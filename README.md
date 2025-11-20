# 🚨 AlertOps

**AlertOps** is an internal **incident management and escalation platform** built with **Spring Boot + PostgreSQL**.  
It tracks incidents, assigns ownership, and automates escalation flows — ensuring that critical issues are resolved on time.

The system is designed with a **scalable architecture**, **role-based access control (RBAC)**, and a roadmap toward **real-time notifications and scheduling**.  
It demonstrates **production-grade backend design patterns**, **workflow automation**, and **database versioning** with Flyway.

---

## ✨ Core Features

- **Incident Management**
    - Create incidents with clear ownership and timelines.
    - Track status from creation → escalation → resolution.

- **Role-based Access Control (RBAC)**
    - Roles: **Admin**, **Engineer**, **Manager**.
    - Fine-grained permissions on creating flows, assigning tasks, and resolving incidents.

- **Escalation Flows**
    - Define escalation chains independently from incidents.
    - Attach flows to incidents for flexible handling.
    - Pre-warns the next user in the chain before reassignment.
    - Auto-escalates unresolved incidents → reassigns ownership and sends notifications.
    - Ensures SLAs are met and responsibilities are clear at every stage.

- **Notification-ready Design**
    - Every escalation step triggers notifications.
    - Everyone in the chain is informed before and after handoff.

---



---

## 📅 Upcoming

- **Notification Service**
    - Support email, Slack, or webhook-based notifications.
    - Real-time visibility for all users in the escalation chain.

- **Dashboards**
    - Centralized UI to track incidents, escalations, and SLAs.

- **Granular RBAC**
    - Fine-grained permissions for enterprise scenarios.

---

## 🚀 Tech Stack

- **Backend**: Java 17, Spring Boot
- **Database**: PostgreSQL + Flyway for schema migrations
- **Scheduling**: Rabbit MQ
- **Messaging/Notifications**: Pluggable design (Email, Slack, Webhooks)
- **ORM**: Hibernate/JPA

---

## 💡 Why This Project Matters

- Built a **production-style incident management system**, not just CRUD.
- Showcases **real-world backend concepts**: workflow automation, RBAC, escalation chains.
- Demonstrates **engineering skills**:
    - Modular service design with clear separation of concerns.
    - Stateful workflow handling with scheduling + notifications.
    - Database versioning with Flyway.
    - RBAC implementation for enterprise-grade access control.
- Mirrors systems like **PagerDuty / OpsGenie** in core functionality.

---

## 🚦 Getting Started

### Prerequisites
- Java 17+
- Maven
- PostgreSQL

### Setup
```bash
# Clone repo
git clone https://github.com/your-username/alert_ops.git
cd alert_ops

# Configure DB and environment in application.yml
# Example: DATABASE_URL, APP_PORT, credentials

# Run migrations with Flyway
./mvnw flyway:migrate

# Start the Spring Boot app
./mvnw spring-boot:run
