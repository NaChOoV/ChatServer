/*
 * Copyright (c) 2019. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The Web Server.
 *
 * @author Diego Urrutia-Astorga.
 * @version 0.0.1
 */
public final class Main {

    /**
     * El logger
     */
    private static final Logger log = LoggerFactory.getLogger(Main.class);


    /**
     *  En el main se crea el servidor y se inicia.
     * @param args
     * @throws IOException
     */
    public static void main(final String[] args) throws IOException {

        ServerChat serverChat = new ServerChat();
        serverChat.start();

    }


    public static final class ServerChat {

        /**
         * Puerto
         */
        private static final int PORT = 9000;
        /**
         * ServerSocket
         */
        private ServerSocket serverSocket;
        /**
         * Lista con todos lso mensajes
         */
        private final List<ChatMessage> messages;



        public ServerChat() throws IOException {

            log.debug("Starting the ServerChat ..");

            this.serverSocket = new ServerSocket(PORT);

            this.messages = new ArrayList<>();
            log.debug("Server started in port {}, waiting for connections ..", PORT);

            /*
            Mensajes de ejemplo
             */
            messages.add(new ChatMessage("Ignacio","Hola"));
            messages.add(new ChatMessage("Pablo","adios"));
            messages.add(new ChatMessage("Javier","Pez"));
        }

        void start() throws IOException {

            while (true) {


                try  {
                    Socket socket = serverSocket.accept();
                    // The remote connection address.
                    final InetAddress address = socket.getInetAddress();

                    log.debug("========================================================================================");
                    log.debug("Connection from {} in port {}.", address.getHostAddress(), socket.getPort());

                    ProcessConection processConection = new ProcessConection(socket);
                    processConection.start();

                } catch (IOException e) {
                    log.error("Error", e);
                    throw e;
                }

            }
        }

        /**
         * Procedimiento que envia todos los datos al cliente, HTML + todos los mensajes
         * @param socket
         * @throws IOException
         */
        private void sendInfo(final Socket socket) throws IOException {

            final PrintWriter pw = new PrintWriter(socket.getOutputStream());
            pw.println("HTTP/1.1 200 OK");
            pw.println("Server: DSM v0.0.1");
            pw.println("Date: " + new Date());
            pw.println("Content-Type: text/html; charset=UTF-8");
            pw.println(); // saldo de linea del protocolo http

            StringBuilder contentBuilder = new StringBuilder();

            List<ChatMessage> ordenado = messages.stream()
                    .sorted(Comparator.comparing(ChatMessage::getLocalDateTime))
                    .collect(Collectors.toList());
            try {
                BufferedReader in = new BufferedReader(new FileReader("src/main/index.html"));
                String str;
                while ((str = in.readLine()) != null) {
                    contentBuilder.append(str);
                }
                in.close();
                for (ChatMessage message : ordenado) {
                    contentBuilder.append("<div>");
                    contentBuilder.append(message.getLocalDateTime().getHour()).append(":");
                    contentBuilder.append(message.getLocalDateTime().getMinute()).append(":");
                    contentBuilder.append(message.getLocalDateTime().getSecond());
                    contentBuilder.append("  ");
                    contentBuilder.append(message.getUsername());
                    contentBuilder.append(": ");
                    contentBuilder.append(message.getMessage());
                    contentBuilder.append("</div>");
                }

            } catch (IOException ignored) {
            }
            String content = contentBuilder.toString();
            pw.println(content);
            pw.flush();

            log.debug("Process ended.");
            socket.close();

        }

        /**
         * Diferentes thread que tomaran la conexion y la atendera.
         */
        class ProcessConection extends Thread{
            /*
            Socket del cliente
             */
            private Socket socket;

            ProcessConection(Socket socket){
                this.socket = socket;
            }

            public void run(){

                try {
                    final List<String> lines = readInputStreamByLines(socket);

                    String[] request = lines.get(0).split(" ");

                    if (request[1].equals("/favicon.ico")) {
                        log.debug("Type of request: {} ignored",request[1]);
                        return;
                    } else if (request[0].equals("POST")) {
                        messages.add(newMessage(lines.get(13)));
                        log.debug("Nuevo mensaje añadido.");

                    }

                    /*
                    log.debug("Size: {}", lines.size());
                    for (int i = 0; i < lines.size(); i++) {
                        log.debug("[{}]info: {}", i, lines.get(i));
                    }
                    */

                    sendInfo(this.socket);


                }
                catch (IOException e){
                    log.error("Error", e);
                }



            }
        }


        /**
         * Funcion que obtiene el String del Body y le saca los parametros del usuario y mensage
         * @param body
         * @return
         */
        public ChatMessage newMessage(String body){

            String[] datos = body.split("&");
            String user = datos[0].split("=")[1].replace("+"," ");
            String message = datos[1].split("=")[1].replace("+"," ");
            return new ChatMessage(user,message);

        }


        /**
         * Read all the input stream.
         *
         * @param socket to use to read.
         * @return all the string readed.
         */
        private static List<String> readInputStreamByLines(final Socket socket) throws IOException {

            final InputStream is = socket.getInputStream();
            // The list of string readed from inputstream.
            final List<String> lines = new ArrayList<>();
            // The Scanner
            final Scanner s = new Scanner(is).useDelimiter("\\A");

            log.debug("Reading the Inputstream ..");
            String line;
            while (true) {
                line = s.nextLine();
                log.debug("lines: {}", line);
                if (line.length() == 0) {
                    if(lines.get(0).contains("POST")){
                        line = s.nextLine();
                        lines.add(line);
                        log.debug("lines: {}", line);
                        break;
                    }else{
                        break;
                    }
                } else {

                    lines.add(line);
                }
            }

            return lines;

        }




        private static class ChatMessage{
            /**
             * fecha y hora local
             */
            private final LocalDateTime timestamp;
            /**
             * Nombre del usuario
             */
            private final String username;
            /**
             * Mensaje
             */
            private final String message;

            /**
             * Constructor que recie parametros y fija la hora en funcion de la llegada del servidor.
             * @param username
             * @param message
             */
            public ChatMessage(String username, String message){
                this.timestamp = LocalDateTime.now();
                this.username = username;
                this.message = message;
            }

            /**
             * Get's y Set's correspondientes
             * @return
             */
            public LocalDateTime getLocalDateTime(){ return this.timestamp; }
            public String getUsername(){ return this.username; }
            public String getMessage(){ return  this.message; }

        } // ChatMessage class


    } // ServerChat class



} // Main class

