import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Server {
    public static Map<SocketChannel, String> clientName= new HashMap<SocketChannel, String>();
    public static Map<Integer, List<SocketChannel>> room = new HashMap<>();
    public static Map<String, SocketChannel> availableNames = new HashMap<>();
    public static void main(String[] args) {

        try {
            Selector selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

            InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", Integer.parseInt(args[0]));
            serverSocketChannel.bind(inetSocketAddress);

            serverSocketChannel.configureBlocking(false);
            int ops = serverSocketChannel.validOps();

            SelectionKey selectionKey = serverSocketChannel.register(selector, ops, null);
            System.out.println("Server Online listening to port: " + args[0]);
            while(true){

                if(selector.select() == 0) continue;

                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> selectionKeyIterator = selectionKeys.iterator();

                while(selectionKeyIterator.hasNext()){
                    SelectionKey mykey = selectionKeyIterator.next();
                    if(mykey.isAcceptable()){
                        SocketChannel socketChannel = serverSocketChannel.accept();
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);
                        System.out.println("Connection Accepted from " + socketChannel.getRemoteAddress());


                    } else if(mykey.isReadable()) {



                        SocketChannel socketChannel = (SocketChannel) mykey.channel();

                        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                        socketChannel.read(byteBuffer);
                        String res = new String(byteBuffer.array()).trim();

                        // Apenas a verificar string vazia

                        if(res.length() == 0){
                            System.out.println("Socket from " + socketChannel.getRemoteAddress() + " closed");
                            socketChannel.socket().close();
                        }else {
                            commands(socketChannel);
                            System.out.println("Message received: " + res);

                            if (res.equals("/exit")) {
                                socketChannel.close();
                                System.out.println("Client sent Exit Command");
                                System.out.println("Closing connection...");
                            }
                        }
                     }
                    selectionKeyIterator.remove();
                }
            }

        } catch (IOException e) {
            System.out.println("Error on opening Socket " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void commands(SocketChannel socketChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        socketChannel.read(buffer);
        if (buffer.limit() == 0) socketChannel.close();




    }
}
