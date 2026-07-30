# Speaker 2 – Data Model & APIs

Thank you. I'm Titus.

Our system has two core tables: **payments** and **payment_history**.

The payments table stores the current payment information, including amount, status, reference and a unique idempotency key.

The payment_history table records every status change, providing a complete audit trail.

Our payment lifecycle is straightforward: Created → Validated → Sent → Completed. If an error occurs, the payment can move to Failed. This state machine ensures valid transitions.

We also implemented four REST APIs: create a payment, get payment details, list payments with filters and pagination, and retrieve payment history.

Now I'll hand over to Yuepeng.
