import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.*;
import java.util.*;

enum state {
    INIT,
    OUTSIDE,
    INSIDE
}

public class Server {

    static private final Charset charset = StandardCharsets.UTF_8;
    static private final CharsetDecoder decoder = charset.newDecoder();
    static private final CharsetEncoder encoder = charset.newEncoder();

    public static Map<SocketChannel, String> clientName= new HashMap<>();
    // Necessário para saber que sala o utilizador se encontra
    public static Map<SocketChannel, Integer> clientRoom = new HashMap<>();

    public static Map<Integer, List<SocketChannel>> room = new HashMap<>();
    public static Map<String, SocketChannel> availableNames = new HashMap<>();
    public static Map<SocketChannel, state> socketState = new HashMap<>();
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
                        commands(socketChannel);


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
        if (buffer.limit() == 0) {
            sendError(socketChannel);
            socketChannel.close();
            return;
        }

        state estado = checkState(socketChannel);
        buffer.flip();
        String cmd = decoder.decode(buffer).toString();
        String[] subCmd = cmd.split(" ");
        // Removing \n
        if(subCmd.length >= 2) subCmd[1] = subCmd[1].substring(0, subCmd[1].length() - 1);
        else{
            if(subCmd[0].length() >= 2) subCmd[0] = subCmd[0].substring(0, subCmd[0].length() - 1);
        }
        System.out.println("After: " + Arrays.toString(subCmd));
        System.out.println("Client State: <" + estado + "> " + estado.equals(state.OUTSIDE));
        switch (estado){
            // ######################## INIT STATE ####################################
            case INIT:
                // Allowed Commands: '/nick' ,  '/bye'
                switch (subCmd[0]){
                    // ---------------- NICK COMMAND -----------------
                    case "/nick":
                        if(availableNames.get(subCmd[1]) == null){
                            clientName.put(socketChannel, subCmd[1]);
                            availableNames.put(subCmd[1], socketChannel);
                            CharBuffer msg = CharBuffer.wrap("Hi " + subCmd[1] + "\n");
                            socketChannel.write(encoder.encode(msg));
                            socketState.remove(socketChannel);
                            socketState.put(socketChannel, state.OUTSIDE);
                        }else{
                            sendError(socketChannel);
                        }
                        break;
                    // -------------- BYE COMMAND -------------------
                    case "/bye":
                        closeSocket(socketChannel);
                        break;
                    default:
                        sendError(socketChannel);
                        break;
                }
                break;

            // ################ OUTSIDE STATE ###################
            case OUTSIDE:
                // Allowed Commands '/join' '/nick' '/bye'
                switch (subCmd[0]) {
                    // ---------------- NICK COMMAND -----------------
                    case "/nick":
                        String name = clientName.get(socketChannel);
                        if(name.equals(subCmd[1])){
                            CharBuffer msg = CharBuffer.wrap("You already are logged as: " + subCmd[1] + "\n");
                            socketChannel.write(encoder.encode(msg));
                        }else if (availableNames.get(subCmd[1]) == null) {
                            availableNames.remove(name);
                            clientName.remove(socketChannel);
                            clientName.put(socketChannel, subCmd[1]);
                            availableNames.put(subCmd[1], socketChannel);
                            CharBuffer msg = CharBuffer.wrap("Hi " + subCmd[1] + "\n");
                            socketChannel.write(encoder.encode(msg));
                        }else{
                            sendError(socketChannel);
                            CharBuffer msg = CharBuffer.wrap("Name: " + subCmd[1] + " already in use\n");
                            socketChannel.write(encoder.encode(msg));
                        }
                        break;
                    // -------------- BYE COMMAND -------------------
                    case "/bye":
                        closeSocket(socketChannel);
                        break;
                    // -------------- JOIN COMMAND ------------------
                    case "/join":
                        removeFromRoom(socketChannel);
                        // Caso não seja possivel converter para inteiro o nº da sala
                        int r;
                        try {
                            r = Integer.parseInt(subCmd[1]);
                        } catch (NumberFormatException e) {
                            sendError(socketChannel);
                            throw new RuntimeException(e);
                        }
                        addToRoom(socketChannel, r);
                        socketState.remove(socketChannel);
                        socketState.put(socketChannel, state.INSIDE);
                        break;
                    default:
                        CharBuffer msg = CharBuffer.wrap("Erro estupido");
                        socketChannel.write(encoder.encode(msg));
                        break;
                }
                break;
            case INSIDE:
                switch (subCmd[0]){
                    case "/bye":
                        closeSocket(socketChannel);
                }
                break;
            default:
                sendError(socketChannel);
                closeSocket(socketChannel);
                throw new IllegalStateException("Unexpected value: <" + estado + ">");
        }




    }

    public static state checkState(SocketChannel socketChannel){
        state estado = socketState.get(socketChannel);
        if(estado == null){
            socketState.put(socketChannel, state.INIT);
            estado = state.INIT;
        }
        return estado;
    }

    // Used to erase every Information about the client before closing
    public static void closeSocket(SocketChannel socketChannel) throws IOException {
        String name = clientName.get(socketChannel);
        // Remove the entry of the Socket -> Name
        clientName.remove(socketChannel);
        // Free Name
        availableNames.remove(name);
        // Remove entry Socket -> Room
        clientRoom.remove(socketChannel);
        // Search for Socket in Room and remove it
        for(Map.Entry<Integer, List<SocketChannel>> e : room.entrySet()){
            Iterator<SocketChannel> it = e.getValue().iterator();
            while(it.hasNext()){
                if(it.next() == socketChannel)  it.remove();
            }
        }
        socketState.remove(socketChannel);
        socketChannel.write(encoder.encode(CharBuffer.wrap("Closing Connection...\n")));
        socketChannel.close();
    }

    public static void removeFromRoom(SocketChannel socketChannel) throws IOException {
        // Catch null in case the user wasn't in a room
        try {
            int r = clientRoom.get(socketChannel);
        }catch (NullPointerException e){
            return;
        }
        String name = clientName.get(socketChannel);
            clientRoom.remove(socketChannel);
            for (Map.Entry<Integer, List<SocketChannel>> e : room.entrySet()) {
                Iterator<SocketChannel> it = e.getValue().iterator();
                while (it.hasNext()) {
                    if (it.next() == socketChannel) it.remove();
                    else {
                        it.next().write(encoder.encode(CharBuffer.wrap("LEFT " + name+"\n")));
                    }
                }
            }
    }

    public static void addToRoom(SocketChannel socketChannel, int r) throws IOException {
        String name = clientName.get(socketChannel);
        for(Map.Entry<Integer, List<SocketChannel>> e : room.entrySet()){
            Iterator<SocketChannel> it = e.getValue().iterator();
            while(it.hasNext()){
                    it.next().write(encoder.encode(CharBuffer.wrap("JOINED "+ name+"\n")));
            }
        }
        room.computeIfAbsent(r, k -> new ArrayList<>()).add(socketChannel);
    }

    public static void sendError(SocketChannel socketChannel) throws IOException {
        CharBuffer msg = CharBuffer.wrap("ERROR\n");
        socketChannel.write(encoder.encode(msg));
    }
}
