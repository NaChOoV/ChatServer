/*
 * Copyright (c) 2019. This code is purely educational, the rights of use are
 * reserved, the owner of the code is Ignacio Fuenzalida Veas-
 * contact: ignacio.fuenzalida@alumnos.ucn.cl.
 * Do not use in production.
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
         * Lista con todos lso mensajes, una representacion de una BD
         */
        private final List<ChatMessage> messages;


        /**
         * Contructor de la clase que manejar el servidor, aca se instanciara el socket y la
         * representacion de la BD en forma de lista. Ademas se agregaran unos mensajes de ejemplo.
         * @throws IOException
         */
        public ServerChat() throws IOException {

            log.debug("Starting the ServerChat ..");

            this.serverSocket = new ServerSocket(PORT);

            this.messages = new ArrayList<>();
            log.debug("Server started in port {}, waiting for connections ..", PORT);

            messages.add(new ChatMessage("Ignacio","Hola"));
            messages.add(new ChatMessage("Pablo","adios"));
            messages.add(new ChatMessage("Javier","Pez"));


        }

        /**
         *
         * @throws IOException
         */
        void start() throws IOException {

            while (true) {

                try  {
                    Socket socket = serverSocket.accept();

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
         * @param socket socket del cliente.
         * @throws IOException
         */
        private void sendHTML(final Socket socket) throws IOException {

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
         * Diferentes thread que tomaran la conexion y la atenderan.
         */
        class ProcessConection extends Thread{
            /*
            Socket del cliente
             */
            private Socket socket;

            ProcessConection(Socket socket){
                this.socket = socket;
            }

            /**
             * Aca se procesara la conexion. El thread obtendra todos los parametros del request y dependiendo
             * del tipo de peticion agregara o no un nuevo emnsaje a la lista.
             */
            public void run(){

                try {
                    final List<String> lines = readInputStreamByLines(socket);

                    String[] request = lines.get(0).split(" ");

                    if (request[1].equals("/favicon.ico")) {
                        log.debug("Type of request: {} ignored",request[1]);
                        return;
                    } else if (request[0].equals("POST")) {
                        for (String line : lines) {
                            if (line.contains("Content-Length:")){
                                newMessage(lines.get(lines.size()-1));
                                break;
                            }
                        }
                    }

                    log.debug("Size: {}", lines.size());
                    for (int i = 0; i < lines.size(); i++) {
                        log.debug("[{}]info: {}", i, lines.get(i));
                    }

                    sendHTML(this.socket);

                }
                catch (IOException e){
                    log.error("Error", e);
                }
            }
        }


        /**
         * Procedimiento que obtiene el String del Body y se obtienen los parametros del usuario y mensage pra luego
         * agregarlos a la lista. En caso de que alguno de los parametros ete vacio se desplegara un mensaje de error
         * y se retornar sin haber agregado el mensaje a la BD.
         * @param body el body del POST
         */
        public void newMessage(String body){

            String[] datos = body.split("&");

            if(datos[0].indexOf("=") == (datos[0].length() - 1)){
                log.debug("Empty user");
                return;
            }
            if(datos[1].indexOf("=") == (datos[1].length() - 1)){
                log.debug("Empty message");
                return;
            }
            String user = datos[0].split("=")[1].replace("+"," ");
            String message = datos[1].split("=")[1].replace("+"," ");
            messages.add(new ChatMessage(user,message));
            log.debug("New message added");

        }


        /**
         * Lectura del InputStream por lineas por parte del cliente.
         * Parte de este codigo fue posible gracias a Alvaro Castillo
         * Contacto: alvaro.castillo@alumnos.ucn.cl
         *
         * @param socket to use to read.
         * @return all the string readed.
         */
        private static List<String> readInputStreamByLines(final Socket socket) throws IOException {

            final InputStream is = socket.getInputStream();
            // The list of string readed from inputstream.
            final List<String> lines = new ArrayList<>();


            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

            log.debug("Reading the InputStream ..");

            String line = "";
            int length = 0;

            while(true){
                line = bufferedReader.readLine();
                //log.debug("InputStream: {}",line);
                if(line.length() > 0){
                    if (line.contains("Content-Length:"))
                        length = Integer.parseInt(line.substring(16));
                }else{
                    break;
                }
                lines.add(line);
            }

            if (length > 0){
                char[] body = new char[length];
                StringBuilder stringBuilder = new StringBuilder(length);
                for (int i = 0; i < length ; i++ ){
                    body[i] = (char) bufferedReader.read();
                }
                lines.add(new String(body));

            }
            return lines;
        }

        /**
         * Clase que representa un mensaje de chat
         */
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
             * @param username nombre del usuario.
             * @param message mensaje a enviar.
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

