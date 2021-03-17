import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;


public class Sender {

    // Maximum Segment Size - Quantity of data from the application layer in the segment
    public static final int MSS = 1022;
    public static DatagramSocket toReceiver;
    public static int timeout;

    public static byte[] extractBytes(String path) throws IOException {

        File file = new File(path);
        FileInputStream fin = null;

        try {
            // create FileInputStream object
            fin = new FileInputStream(file);
            byte fileContent[] = new byte[(int) file.length()];
            fin.read(fileContent);
            String s = new String(fileContent);
            return fileContent;
        } catch (FileNotFoundException e) {
            System.out.println("File cannot found" + e);
        } catch (IOException ioe) {
            System.out.println("Exception while reading file " + ioe);
        } finally {
            try {
                if (fin != null) {
                    fin.close();
                }
            } catch (IOException ioe) {
                System.out.println("Error while closing the stream: " + ioe);
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {

        int lastSent = 0;

        // Sequence number of the last acked packet
        int waitingForAck = 0;
        String PATH = args[0];
        int PORT = Integer.parseInt(args[1]);
        int WINDOW_SIZE = Integer.parseInt(args[2]);
        int retransmission = Integer.parseInt(args[3]);
        //int WINDOW_SIZE = 20;
        byte[] fileBytes = extractBytes(PATH);


        System.out.println("Data size: " + fileBytes.length + " bytes");

        // Last packet sequence number
        int lastSeq = (int) Math.ceil((double) fileBytes.length / MSS);
        InetAddress receiverAddress = InetAddress.getByName("localhost");
        System.out.println("Number of packets to send: " + lastSeq);
        ArrayList<DatagramPacket> packets = new ArrayList<>();
        for (int i = 0; i < lastSeq; i++) {
            ByteBuffer buffers = ByteBuffer.allocate(1024);
            buffers.putShort((short) (i + 1));
            int start = i * 1022;
            int length = Math.min(1022, fileBytes.length - start);
            buffers.put(fileBytes, start, length);
            packets.add(new DatagramPacket(buffers.array(), length + 2, receiverAddress, PORT));
        }

        //Datagram socket initialization
        toReceiver = new DatagramSocket();


        while (true) {

            // Sending loop
            while (lastSent - waitingForAck < WINDOW_SIZE && lastSent < lastSeq) {

                // Create the packet
                DatagramPacket packet = packets.get(lastSent);
                //packet = new DatagramPacket(sendData, sendData.length, receiverAddress, 60000);


               // System.out.println("Sending packet with sequence number " + lastSent + " and size " + packet.getData().length + " bytes");
                //Send the packet
                toReceiver.send(packet);

                lastSent++;

            } // End of sending while


            byte[] array = new byte[2];
            DatagramPacket ack = new DatagramPacket(array, 0, 2);


            try {
                toReceiver.setSoTimeout(retransmission);
                // Receive the packet
                toReceiver.receive(ack);
                int ackNum = ByteBuffer.wrap(ack.getData()).getShort();
                if (ackNum == lastSeq) {

                   // System.out.println("Gelen ack:" + ackNum);

                    break;
                }

                waitingForAck = Math.max(waitingForAck, ackNum);
                //System.out.println("waitingForack" + waitingForAck);

            } catch (SocketTimeoutException e) {
                // then send all the sent but non-acked packets

                for (int i = waitingForAck; i < lastSent; i++) {


                    byte[] header = new byte[2];
                    header[0] = (byte) ((lastSent >> 8) & 0xff);
                    header[1] = (byte) (lastSent & 0xff);
                    ByteBuffer wrapp = ByteBuffer.wrap(header);
                    short num = wrapp.getShort();
                    byte[] filePacketBytes = new byte[MSS];
                    filePacketBytes = Arrays.copyOfRange(fileBytes, waitingForAck, lastSent - 1);

                    byte[] sendData = new byte[1024];
                    sendData[0] = header[0];
                    sendData[1] = header[1];
                    for (int j = 2; j < lastSent - waitingForAck; j++) {
                        sendData[j] = filePacketBytes[j - 2];
                    }


                    // Create the packet
                    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, receiverAddress, PORT);


                    toReceiver.send(packet);
                    System.out.println("REsending packet with sequence number " + lastSent + " and size " + sendData.length + " bytes");
                }
            }


        }
        byte[] bytes = new byte[2];
        toReceiver.send(new DatagramPacket(bytes, 2, receiverAddress, PORT));

        System.out.println("Finished transmission");

    }

    static class SendThread extends Thread {
        private DatagramPacket packet;

        SendThread(DatagramPacket packet) {
            this.packet = packet;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    toReceiver.send(packet);
                    Thread.sleep(Sender.timeout);
                }
            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                System.out.println("IOException occurred. Terminating the program.");
                System.exit(3);
            }
        }
    }
    static class ReceiveThread extends Thread {
        private DatagramPacket packet;

        ReceiveThread(DatagramPacket packet) {
            this.packet = packet;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    toReceiver.receive(packet);
                    Thread.sleep(Sender.timeout);
                }
            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                System.out.println("IOException occurred. Terminating the program.");
                System.exit(3);
            }
        }
    }
}


