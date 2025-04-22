package Proxy;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ProxyServer {
    private static final HashMap<String, byte[]> cache = new HashMap<>();
    private static final int timeout = 10000;
    private static final int blockSize = 512;
    static ConcurrentHashMap<Integer, Long> sentTime = new ConcurrentHashMap<>();
    static AtomicReference<Double> rtt = new AtomicReference<>(800.0);
    //static AtomicLong rtt = new AtomicLong(700);
    static ConcurrentHashMap<Integer, Boolean> retransmitList = new ConcurrentHashMap<>();
    static ConcurrentHashMap<Integer, Boolean> acks = new ConcurrentHashMap<>();
    public static void main(String[] args) throws IOException {
        DatagramPacket received;
        String urlReq;

        DatagramSocket socket = new DatagramSocket(26915);
        socket.setSoTimeout(timeout);
        System.out.println("Proxy server is listening");

        while(true){
            byte[] buffer = new byte[1024];
            received = new DatagramPacket(buffer, buffer.length);

            try{
                socket.receive(received);
                System.out.println(received.getAddress() + "." + received.getPort());
                handleRequest(socket, received);
            }catch (SocketTimeoutException e){
                continue;
            }catch (Exception e){
                System.out.println("Error " + e);
                e.printStackTrace();
            }

        }
    }

    private static void handleRequest(DatagramSocket socket, DatagramPacket received) {
        System.out.println("Request received");
        Map<String, String> options;
        byte[] datatoSend;
        InetAddress clientAdd = received.getAddress();
        int clientPort = received.getPort();
        try {
            ByteBuffer buff = ByteBuffer.wrap(received.getData(), 0, received.getLength());
            int opcode = buff.getShort() & 0xFFFF;
            System.out.println("opcode:" + opcode);
            if (opcode != 1) {
                System.out.println("Unsupported opcode" + opcode);
                return;
            }
            options = parseData(buff);
            if(options.containsKey("mode")) {
                if (!(options.get("mode").equals("octet"))) {
                    System.out.println("The mode is  not octet");
                    return;
                }
            }

            System.out.println(options.get("url"));

            if (cache.containsKey(options.get("url"))) {
                datatoSend = cache.get(options.get("url"));
                System.out.println("Found it cached yayyy");
            } else {
                datatoSend = extractFile(options.get("url"), options.get("filename"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        int windowSize = 5;
        AtomicInteger first = new AtomicInteger(1);

        int blockSize = 512;
        int sessionID = Integer.parseInt(options.get("senderID"));
        int drop = Integer.parseInt(options.get("drop"));
        int dropBytes = 0;
        if (drop == 1) {
            dropBytes = (int) Math.ceil((double) (datatoSend.length) * 0.1);
            datatoSend = Arrays.copyOfRange(datatoSend, dropBytes, datatoSend.length);
        }
        AtomicInteger sequence = new AtomicInteger(1);
        AtomicBoolean completion = new AtomicBoolean(false);

        int totalBlocks = (int) Math.ceil((double) datatoSend.length / blockSize);
        System.out.println("total blocks: " + totalBlocks);
        ConcurrentHashMap<Integer, byte[]> blocksData = new ConcurrentHashMap<>();
        for (int i = 1; i <= totalBlocks; i++) {
            int start = (i-1)*blockSize;
            int end = Math.min(start + blockSize, datatoSend.length);
            blocksData.put(i, Arrays.copyOfRange(datatoSend, start, end));
        }

        Thread sender = new Thread(() -> {
            try {
                while (sequence.get() <= totalBlocks && !completion.get()) {
                    if (!retransmitList.isEmpty()) {
                        for (Integer seq : new ArrayList<>(retransmitList.keySet())) {
                            if (seq < first.get() + windowSize) {
                                sendPacket(socket, clientAdd, clientPort, seq, blocksData.get(seq));
                                retransmitList.remove(seq);
                            }
                        }
                    }
                    while (sequence.get() < first.get() + windowSize && sequence.get() <= totalBlocks) {
                        int currentSeq = sequence.get();
                        sendPacket(socket, clientAdd, clientPort, currentSeq, blocksData.get(currentSeq));
                        sequence.getAndIncrement();
                    }
                    try{
                        Thread.sleep(10);
                    }catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread receiver = new Thread(() -> {
            try{
                while(!completion.get()) {
                    byte[] ack = new byte[4];
                    DatagramPacket ackPack = new DatagramPacket(ack, ack.length);
                    try {
                        socket.receive(ackPack);
                        ByteBuffer buf = ByteBuffer.wrap(ackPack.getData());
                        short opc = buf.getShort();
                        if (opc == (short) 4) {
                            int ackSeq = buf.getShort() & 0xFFFF;
                            Long sentTimev = sentTime.get(ackSeq);
                            if (sentTimev != null) {
                                long srtt = ((System.nanoTime()) - sentTimev) / 1000000;
                                rtt.set(0.8 * rtt.get() + 0.2 * srtt);
                                acks.put(ackSeq, true);

                                while (acks.containsKey(first.get())) {
                                    acks.remove(first.get());
                                    sentTime.remove(first.get());
                                    first.incrementAndGet();
                                }
                                if (first.get() > totalBlocks) {
                                    completion.set(true);
                                    System.out.println("Transfer complete");
                                }
                            }
                        }

                    } catch (SocketTimeoutException e) {
                    } catch (IOException e) {
                        if (!completion.get()) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }catch (RuntimeException e){
                completion.set(true);
                throw e;
            }
        });

        Thread timeOuts = new Thread(() -> {
            while (!completion.get()) {
                long now = System.nanoTime();
                for (Map.Entry<Integer, Long> entry : new HashMap<>(sentTime).entrySet()) {
                    int seq = entry.getKey();
                    long sentTime = entry.getValue();

                    if (!acks.containsKey(seq)) {
                        double timeElapsed = (now - sentTime) / 1000000;
                        if (timeElapsed > rtt.get() * 2) {
                            retransmitList.put(seq, true);
                        }
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        sender.start();
        receiver.start();
        timeOuts.start();

        try {
            sender.join();
            receiver.join();
            timeOuts.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    private static void sendPacket(DatagramSocket socket, InetAddress address, int port, int sequence, byte[] data) throws IOException {

        if(data == null){
            System.out.println("Can not send null data for block");
            return;
        }

        try {
            ByteBuffer packet = ByteBuffer.allocate(516);
            packet.putShort((short) 3);
            packet.putShort((short) sequence);
            packet.put(data);
            byte[] packetData = Arrays.copyOf(packet.array(), packet.position());
            sentTime.put(sequence, System.nanoTime());
            socket.send(new DatagramPacket(packetData, packetData.length, address, port));
            System.out.println("sent block " + sequence );
        } catch (BufferOverflowException e){
            throw new IOException("Packet too large?", e);
        }
    }

    private static Map<String, String> parseData(ByteBuffer b){
        Map<String, String> options = new HashMap<>();
        options.put("fileName", read(b));
        options.put("mode", read(b));
        while (b.hasRemaining()){
            String op = read(b);
            if (op.isEmpty()){
                break;
            }
            options.put(op, read(b));
        }
        return options;
    }

    private static String read(ByteBuffer b){
        StringBuilder sb = new StringBuilder();
        while (b.hasRemaining()){
            char c = (char) b.get();
            if(c == 0) break;
            sb.append(c);
        }
        return sb.toString();
    }
    ///
    public static byte[] checkCache(String url) throws IOException {
        if(cache.containsKey(url)){
            return cache.get(url);
        }
        return null;
    }


    public static byte[] extractFile(String u, String fileName) throws IOException {
        URL url = new URL(u);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] b = new byte[2048];
        int length;
        while((length = inputStream.read(b)) != -1){
            byteArrayOutputStream.write(b, 0, length);
        }
        inputStream.close();
        byteArrayOutputStream.close();
        cache.put(u, byteArrayOutputStream.toByteArray());
        return byteArrayOutputStream.toByteArray();
    }


}