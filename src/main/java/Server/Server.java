package Server;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;

public class Server {
    public static final int port = 8080;
    public static void main(String[] args) throws IOException {
        try(ServerSocket socket = new ServerSocket(port)){
            System.out.println("Server is running");
            while(true){
                Socket proxyClient = socket.accept();
                DataInputStream inputStream = new DataInputStream(proxyClient.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(proxyClient.getOutputStream());
                String url = inputStream.readUTF();
                String fileName = getFileName(url);

                File file = new File("./Server" + fileName);

                if(file.exists() && file.isFile()){
                    byte[] fileinBytes = Files.readAllBytes(file.toPath());
                    outputStream.write(fileinBytes);
                }else {
                    System.out.println("file not found");
                    outputStream.write(-1);
                }

            }
        }catch (IOException e){

        }
    }

    private static String getFileName(String u) throws MalformedURLException {
        URL url = new URL(u);
        String path = url.getPath();
        return path.substring(path.lastIndexOf('/')+1);
    }
}
