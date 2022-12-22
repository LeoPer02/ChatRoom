import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Server {
    public static void main(String[] args) {

        try {
            Selector selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

            InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", Integer.parseInt(args[0]));
            serverSocketChannel.bind(inetSocketAddress);

            serverSocketChannel.configureBlocking(false);
            int ops = serverSocketChannel.validOps();

            SelectionKey selectionKey = serverSocketChannel.register(selector, ops, null);

            while(true){
                System.out.println("Server Online listening to port: " + args[0]);

                selector.select();

                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> selectionKeyIterator = selectionKeys.iterator();

                
            }

        } catch (IOException e) {
            System.out.println("Error on opening Socket " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
