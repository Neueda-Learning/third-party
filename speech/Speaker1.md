# Speaker 1 – Opening, Background & Architecture

Good morning everyone.

We're Team **Payment Processing System**. Today we'd like to present our backend engineering project: **Third-Party Payment Processing System**.

My name is Stewie. Together with Titus, Yuepeng and Merry, we'll introduce our project.

Imagine a customer clicking the **Pay** button twice because the network is slow. Without proper protection, the system could create two payments, leading to duplicate charges, inconsistent records and reduced customer trust.

To solve this problem, we built a payment processing system that focuses on **reliability, traceability and maintainability**.

Our system includes idempotent payment creation, a payment state machine, automatic timeout handling and complete payment history.

The project uses Spring Boot, Java 21, JDBC Template, MySQL and a simple HTML frontend. We adopted a layered architecture: Frontend → Controller → Service → DAO → Database. This keeps responsibilities clear and makes the system easy to maintain.

Now I'll hand over to Titus.
