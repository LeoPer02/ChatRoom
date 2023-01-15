import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    Socket socket;
    DataOutputStream dataOutputStream;
    BufferedReader bufferedReader;
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
        socket = new Socket(server, port);
        dataOutputStream = new DataOutputStream(socket.getOutputStream());
        bufferedReader = new BufferedReader( new InputStreamReader (socket.getInputStream()));
    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        String tmp[] = message.split(" ");
        System.out.println(tmp[0]);
        if(message.length() > 1 && message.charAt(0) == '/' && !Objects.equals(tmp[0], "/nick") && !Objects.equals(tmp[0], "/join") && !Objects.equals(tmp[0], "/leave") && !Objects.equals(tmp[0], "/bye") && !Objects.equals(tmp[0], "/priv")){
            message = "/" + message + "\n";
            dataOutputStream.write(message.getBytes());
            //("/" + message + "\n");
            dataOutputStream.flush();
            if(Objects.equals(tmp[0], "/bye")) frame.dispose();
        }else{
            message += '\n';
            dataOutputStream.write(message.getBytes());
            dataOutputStream.flush();
        }
    }

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
        String messageFromServer;
        while((messageFromServer = bufferedReader.readLine()) != null) {
            //messageFromServer = bufferedReader.readLine();
            printMessage(messageFromServer + '\n');
        }
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
