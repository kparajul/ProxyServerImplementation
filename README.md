# ProxyServerImplementation

Write a proxy server program, that relays files/pages. To demonstrate, you'll need a client and a server program:
The proxy server awaits connections.
A client connects, sends a URL.
The proxy server gets the corresponding page/file using HTTP. The proxy server caches (immediately sending instead of fetching) at least the most recent request; optionally more.
The proxy sends the page/file to the client, that then displays it. For purposes of this assignment, it is OK if the only kinds of files displayed are images (for example jpg).

Wherever applicable, use the commands and protocol for TFTP (IETF RFC 1350), with the following modifications. You will need to design and use additional packet header information than that in TFTP; use the IETF 2347 TFTP Options Extension when possible.

Use TCP-style sliding windows rather than the sequential acks used in TFTP.
Use a TCP-style RTO scheme for retransmission timeouts
Arrange that each session begins with a sender ID and (random) number exchange to generate a key to be used for encrypting data. You can use Xor to create key, or anything better, and use this as the basis for randomized xoring (as in project 1) or similar protocols.
Support only binary (octet) transmission.
Support a command line argument controlling whether to pretend to drop 1 percent of the packets;
When receiving files, place them in a temporary directory (for example /tmp) to avoid overwriting in place on shared file systems. Validate that they have the same contents (see linux "cmp" command).
