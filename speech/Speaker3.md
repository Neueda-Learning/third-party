# Speaker 3 – Demo & Query Module

Hello everyone. I'm Yuepeng.

I was responsible for the payment query module.

During the demonstration, we'll first create a payment. Then we'll search payments using filters such as payment status. The list supports pagination and sorting, making it easy to manage large datasets.

Users can also open a payment to view detailed information and its complete history.

Another key feature is **idempotency**. Every request contains an Idempotency-Key. If the same request is sent twice, the system returns the existing result instead of creating another payment. If the same key is used with different data, the server returns **409 Conflict**. A database UNIQUE constraint guarantees this behavior.

Now I'll hand over to Merry.
