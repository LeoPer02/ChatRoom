import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.Buffer;
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
    public static Map<SocketChannel, String> clientRoom = new HashMap<>();

    public static Map<String, List<SocketChannel>> room = new HashMap<>();
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
            closeSocket(socketChannel);
            return;
        }

        state estado = checkState(socketChannel);
        buffer.flip();
        String cmd = decoder.decode(buffer).toString();
        String[] subCmd = cmd.split(" ");
        // Removing \n
        int l = subCmd.length;
        subCmd[l-1] = subCmd[l-1].substring(0, subCmd[l-1].length() - 1);
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
                            CharBuffer msg = CharBuffer.wrap("OK\n");
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
                /*
                *
                *
                *
                *
                *  Mudar as salas para Strings (Nomes tipo 'C++', 'Sala_de_Convivio') em vez
                *  de Integers
                *
                *
                *
                *
                *
                *
                *
                *
                *
                * */
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
                            CharBuffer msg = CharBuffer.wrap("OK\n");
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
                        String r;
                        try {
                            r = subCmd[1];
                        } catch (NumberFormatException e) {
                            sendError(socketChannel);
                            return;
                        }
                        addToRoom(socketChannel, r);
                        socketState.remove(socketChannel);
                        socketState.put(socketChannel, state.INSIDE);
                        socketChannel.write(encoder.encode(CharBuffer.wrap("OK\n")));
                        break;
                    case "/priv":
                        String emissor = clientName.get(socketChannel);
                        SocketChannel recetorSoc = availableNames.get(subCmd[1]);
                        if(recetorSoc == null){
                            sendError(socketChannel);
                            break;
                        }
                        String msg = getMessage(cmd);
                        sendPrivMessage(recetorSoc, msg, emissor);
                        socketChannel.write(encoder.encode(CharBuffer.wrap("OK\n")));
                        break;
                    default:
                        sendError(socketChannel);
                        break;
                }
                break;
            case INSIDE:
                // Allowed Commands
                switch (subCmd[0]){
                    case "/bye":
                        removeFromRoom(socketChannel);
                        closeSocket(socketChannel);
                        break;
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
                            warnChangeName(socketChannel, name, subCmd[1]);

                        }else{
                            sendError(socketChannel);
                            CharBuffer msg = CharBuffer.wrap("Name: " + subCmd[1] + " already in use\n");
                            socketChannel.write(encoder.encode(msg));
                        }
                        break;
                    case "/leave":
                        removeFromRoom(socketChannel);
                        break;
                    case "/join":
                        removeFromRoom(socketChannel);
                        // Caso não seja possivel converter para inteiro o nº da sala
                        String r;
                        try {
                            r = subCmd[1];
                        } catch (NumberFormatException e) {
                            sendError(socketChannel);
                            return;
                        }
                        addToRoom(socketChannel, r);
                        socketState.remove(socketChannel);
                        socketState.put(socketChannel, state.INSIDE);
                        break;
                    case "/priv":
                        String emissor = clientName.get(socketChannel);
                        SocketChannel recetorSoc = availableNames.get(subCmd[1]);
                        if(recetorSoc == null){
                            sendError(socketChannel);
                            break;
                        }
                        String msg = getMessage(cmd);
                        sendPrivMessage(recetorSoc, msg, emissor);
                        socketChannel.write(encoder.encode(CharBuffer.wrap("OK\n")));
                        break;
                    default:
                        sendMessage(socketChannel, cmd);
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
        for(Map.Entry<String, List<SocketChannel>> e : room.entrySet()){
                Iterator<SocketChannel> it = e.getValue().iterator();
                while (it.hasNext()) {
                    if (it.next() == socketChannel) it.remove();
                }
        }
        socketState.remove(socketChannel);
        socketChannel.write(encoder.encode(CharBuffer.wrap("BYE\n")));
        socketChannel.close();
    }

    public static void removeFromRoom(SocketChannel socketChannel) throws IOException {
        // Catch null in case the user wasn't in a room
        String r;
        try {
            //System.out.println("Getting in Removal " + clientRoom.toString());
            r = clientRoom.get(socketChannel);
            //System.out.println("Room: " + r);
        }catch (NullPointerException e){
            return;
        }
        String name = clientName.get(socketChannel);
        clientRoom.remove(socketChannel);
        for (Map.Entry<String, List<SocketChannel>> e : room.entrySet()) {
            if(Objects.equals(e.getKey(), r)) {
                Iterator<SocketChannel> it = e.getValue().iterator();
                while (it.hasNext()) {
                    SocketChannel s = it.next();
                    if (s == socketChannel) {
                        socketChannel.write(encoder.encode(CharBuffer.wrap("OK\n")));
                        it.remove();
                    } else {
                        s.write(encoder.encode(CharBuffer.wrap("LEFT " + name + "\n")));
                    }
                }
            }
        }
        socketState.remove(socketChannel);
        socketState.put(socketChannel, state.OUTSIDE);

        //System.out.println("After removing: " + room.toString());
    }

    public static void addToRoom(SocketChannel socketChannel, String r) throws IOException {
        String name = clientName.get(socketChannel);
        for(Map.Entry<String, List<SocketChannel>> e : room.entrySet()){
            if(Objects.equals(e.getKey(), r)) {
                Iterator<SocketChannel> it = e.getValue().iterator();
                while (it.hasNext()) {
                    it.next().write(encoder.encode(CharBuffer.wrap("JOINED " + name + "\n")));
                }
            }
        }
        room.computeIfAbsent(r, k -> new ArrayList<>()).add(socketChannel);
        clientRoom.put(socketChannel, r);
    }

    public static void sendError(SocketChannel socketChannel) throws IOException {
        CharBuffer msg = CharBuffer.wrap("ERROR\n");
        socketChannel.write(encoder.encode(msg));
    }

    public static void warnChangeName(SocketChannel socketChannel, String OldName, String NewName) throws IOException {
        String r;
        try {
            //System.out.println("Getting in Removal " + clientRoom.toString());
            r = clientRoom.get(socketChannel);
            //System.out.println("Room: " + r);
        }catch (NullPointerException e){
            return;
        }
        for (Map.Entry<String, List<SocketChannel>> e : room.entrySet()) {
            if(Objects.equals(e.getKey(), r)) {
                Iterator<SocketChannel> it = e.getValue().iterator();
                while (it.hasNext()) {
                    SocketChannel s = it.next();
                    if (s == socketChannel) s.write(encoder.encode(CharBuffer.wrap("OK\n")));
                    else s.write(encoder.encode(CharBuffer.wrap("NEWNICK " + OldName + " " + NewName + "\n")));
                }
            }
        }

    }

    public static void sendMessage(SocketChannel socketChannel, String msg) throws IOException {
        if(msg.charAt(0) == '/'){
            msg = msg.substring(1);
        }

        String r;
        try {
            //System.out.println("Getting in Removal " + clientRoom.toString());
            r = clientRoom.get(socketChannel);
            //System.out.println("Room: " + r);
        }catch (NullPointerException e){
            return;
        }

        String name = clientName.get(socketChannel);

        for (Map.Entry<String, List<SocketChannel>> e : room.entrySet()) {
            if(Objects.equals(e.getKey(), r)) {
                Iterator<SocketChannel> it = e.getValue().iterator();
                while (it.hasNext()) {
                    SocketChannel s = it.next();
                    // Caso usemos interface gráfica, retirar o if
                    /*if (s != socketChannel)*/ s.write(encoder.encode(CharBuffer.wrap(name + ": " + msg)));
                }
            }
        }

    }

    public static void sendPrivMessage(SocketChannel socketChannel, String msg, String emissor) throws IOException {
        socketChannel.write(encoder.encode(CharBuffer.wrap("PRIVATE " + emissor + ": " + msg)));
    }

    public static String getMessage(String msg){
        int count = 0, i;
        for(i = 0; i < msg.length(); i++){
            if(count == 2) break;
            if(msg.charAt(i) == ' ') count++;
        }
        return msg.substring(i);
    }


}
