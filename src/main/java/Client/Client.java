package Client;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class Client {
    static boolean completion = false;
    static HashMap<Integer, byte[]> receivedData = new HashMap<>();

    public static void main(String[] args) throws SocketException, UnknownHostException {
        InetAddress serverAddress = InetAddress.getByName("localhost");
        int serverPort = 26915;
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the url of the image you want: ");
        String url = sc.nextLine();
        int drop;
        while (true) {
            System.out.println("Drop 10% of packets? 'y' or 'n'");
            String temp = sc.nextLine();
            if (temp.equals("y")) {
                drop = 1;
                break;
            } else if (temp.equals("n")) {
                drop = 0;
                break;
            }else {
                System.out.println("wrong input");
            }
        }
        String fileName = url.substring(url.lastIndexOf('/') + 1);

        short senderID = (short) new Random().nextInt(Short.MAX_VALUE + 1);
        int encryptionNum = new Random().nextInt();

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putShort((short) 1); //RRQ
        putString(buffer, fileName);
        putString(buffer, "octet");
        putString(buffer, "senderID");
        putString(buffer, String.valueOf(senderID));
        putString(buffer, "encryption");
        putString(buffer, String.valueOf(encryptionNum));
        putString(buffer, "drop");
        putString(buffer, String.valueOf(drop));
        putString(buffer, "url");
        putString(buffer, url);
        byte[] sendPacket = Arrays.copyOf(buffer.array(), buffer.position());

        try {
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(sendPacket, sendPacket.length, serverAddress, serverPort);
            socket.send(packet);
            System.out.println("request sent");
            handleReceived(socket, serverAddress, serverPort, fileName);
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    private static void handleReceived(DatagramSocket socket, InetAddress address, int port, String fileName) throws IOException {
        byte[] receiveBuf = new byte[516];
        DatagramPacket received = new DatagramPacket(receiveBuf, receiveBuf.length);
        while(!completion){
            Arrays.fill(receiveBuf, (byte) 0);
            received.setData(receiveBuf);
            received.setLength(receiveBuf.length);

            socket.receive(received);
            ByteBuffer buffer = ByteBuffer.wrap(received.getData(), 0, received.getLength());
            short opc = buffer.getShort();
            if(opc == 3){
                int sequenceNum = buffer.getShort() & 0xFFFF;
                int length = received.getLength()-4;
                byte[] data = new byte[length];
                buffer.get(data, 0, length);
                receivedData.put(sequenceNum, data);
                System.out.println("received seq: " + sequenceNum);

                ByteBuffer sendAck = ByteBuffer.allocate(4);
                sendAck.putShort((short) 4);
                sendAck.putShort((short) sequenceNum);
                byte[] ackData = sendAck.array();
                DatagramPacket ackPck = new DatagramPacket(ackData, ackData.length, address, port);
                socket.send(ackPck);

                if(data.length < 512){
                    completion=true;
                    //display(receivedData);
                    save(fileName, receivedData);
                    System.out.println("Transfer complete");
                }
            }

        }
    }

//    private static void display(HashMap<Integer, byte[]> data) throws IOException {
//        List<Integer> keys = new ArrayList<>(data.keySet());
//        Collections.sort(keys);
//
//        // Merge all bytes into one array
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        for (int key : keys) {
//            byte[] block = data.get(key);
//            if (block != null) {
//                outputStream.write(block);
//            }
//        }
//
//        byte[] imageBytes = outputStream.toByteArray();
//
//        // Convert bytes into image
//        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
//        BufferedImage img = ImageIO.read(bais);
//        if (img == null) {
//            System.out.println("Could not parse image from received data.");
//            return;
//        }
//
//        // Show image in a window
//        JFrame frame = new JFrame("Received Image");
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.setSize(img.getWidth(), img.getHeight());
//
//        JLabel label = new JLabel(new ImageIcon(img));
//        frame.getContentPane().add(label, BorderLayout.CENTER);
//        frame.pack();
//        frame.setVisible(true);
//    }

    private static void save(String fileName, HashMap<Integer, byte[]> data) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        List<Integer> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        for (int key:keys){
            byte[] block = data.get(key);
            if(block != null){
                fileOutputStream.write(block);
            }
        }
        fileOutputStream.close();
        System.out.println("File saved");
    }

    private static void putString(ByteBuffer buffer, String s){
        buffer.put(s.getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) 0);
    }

}
