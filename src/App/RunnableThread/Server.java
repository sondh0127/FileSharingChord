package App.RunnableThread;

import App.Components.*;
import javafx.util.Pair;

import java.io.*;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread that receives the message from the node and sends response message appropriately
 */
public class Server implements Runnable{

    private Node myNode = null;
    private Socket socket = null;

    public Server(Node node, Socket socket) {
        this.myNode = node;
        this.socket = socket;
    }

    public void run() {
        try {
            // Receive file's file location from the socket
//            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
//            String message = (String) in.readObject();

            // Receive message from the client
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            Object[] messageArray = (Object[]) objectInputStream.readObject();
//            System.out.println(myNode.getNodeName() + "-SERVER: Message received: "+ messageArray[0]);
            if (messageArray.length >= 2) {
//                System.out.println("SERVER: Object received: " + messageArray[1]);
            }

            if (messageArray[0] != null) {
                Object response = processMessageRequest(messageArray);
//                System.out.println(myNode.getNodeName() + "-SERVER: Response message: " + response + "\n");

                // Send response to the client
                if (messageArray[0] != MessageType.DOWNLOAD_FILE) {
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                    objectOutputStream.writeObject(response);
                    objectOutputStream.flush();

                    // Wait 50 millisecs to before closing streams
                    Thread.sleep(50);
                    if (objectOutputStream != null) {objectOutputStream.close();}
                }
            }

            if (objectInputStream != null) {objectInputStream.close();}
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Object processMessageRequest(Object[] messageArray) {
        Object response = null;
        try {
//            System.out.println(myNode.getNodeName() + " - SERVER: Processing message array: " + messageArray);
            MessageType message = (MessageType) messageArray[0];
            if (message == MessageType.CHECKING_IF_PORT_IS_AVAILABLE) {
                return MessageType.OK;
            }
            long id;

            switch (message) {
                // Check if ID belongs to a node in the network
                case DOES_ID_EXIST:
                    id = (long) messageArray[1];
                    response = MessageType.NOT_EXIST;
                    System.out.println(myNode.getNodeName() + " - SERVER: Checking if ID exists: " + id);
                    response = myNode.checkIfIdExists(id);
                    break;


                // New node is joining the network, find its successor
                // Response is the successor node
                case FIND_SUCCESSOR:
                    id = (long) messageArray[1];
//                    System.out.println(myNode.getNodeName() + " - SERVER: A node wants to find successor of id=" + id);
                    Node successor = myNode.findSuccessorOf(id);
//                    System.out.println(myNode.getNodeName() + " - SERVER: Found new node's successor: " + successor.getNodeName() + ", address:" + successor.getAddress().getAddress().getHostAddress() + ":" + successor.getAddress().getPort());
                    response = successor;
                    break;


                // Find closest finger preceding id
                // Response is a node
                case CLOSEST_PRECEDING_FINGER:
                    id = (long) messageArray[1];
//                    System.out.println(myNode.getNodeName() + "-SERVER: A node wants to find closest finger preceding id=" + id);
                    Node closestFinger = myNode.closestPrecedingFingerOf(id);
//                    System.out.println(myNode.getNodeName() + "-SERVER: Found closest finger preceding id: " + closestFinger.getNodeName() + ", address:" + closestFinger.getAddress().getAddress().getHostAddress() + ":" + closestFinger.getAddress().getPort());
                    response = closestFinger;
                    break;


//                // Response is a my successor Node
                case GET_YOUR_SUCCESSOR:
//                    System.out.println(myNode.getNodeName() + "-SERVER: A node wants to get my successor " + myNode.getNodeName());
                    response = myNode.getSuccessor();
//                    System.out.println(myNode.getNodeName() + "-SERVER: Return my successor: " + myNode.getSuccessor().getNodeName() + ", address:" + myNode.getSuccessor().getAddress().getAddress().getHostAddress() + ":" + myNode.getSuccessor().getAddress().getPort());
                    break;


                // Return my predecessor
                // Response is my predecessor a Node
                case GET_YOUR_PREDECESSOR:
//                    System.out.println(myNode.getNodeName() + "-SERVER: A node wants to find predecessor of me " + myNode.getNodeName());
                    response = myNode.getPredecessor();
//                    System.out.println(myNode.getNodeName() + "-SERVER: Return my predecessor: " + myNode.getPredecessor().getNodeName() + ", address:" + myNode.getPredecessor().getAddress().getAddress().getHostAddress() + ":" + myNode.getPredecessor().getAddress().getPort());
                    break;


                // My predecessor has changed, updating my predecessor
                case I_AM_YOUR_NEW_PREDECESSOR:
                    Node pre = (Node) messageArray[1];
                    myNode.setPredecessor(pre);
                    response = MessageType.GOT_IT;
                    System.out.println(myNode.getNodeName() + "-SERVER: new predecessor id=" + pre.getNodeName() + ", address:" + pre.getAddress().getAddress().getHostAddress() + ":" + pre.getAddress().getPort());

                    // Check if I need to update my successor
//                    if (myNode.getNodeId() == myNode.getSuccessor().getNodeId()) { // Only I was in the network, now I have a new successor
//                        myNode.setSuccessor(pre);
//                    }
                    break;


                // My successor has changed, updating my successor
                case I_AM_YOUR_NEW_SUCCESSOR:
                    Node suc = (Node) messageArray[1];
                    myNode.setSuccessor(suc);
                    // Update my 1-st finger as well
                    myNode.getFingerTable().updateEntryNode(1, suc);
                    response = MessageType.GOT_IT;
                    System.out.println(myNode.getNodeName() + "-SERVER: new predecessor id=" + suc.getNodeName() + ", address:" + suc.getAddress().getAddress().getHostAddress() + ":" + suc.getAddress().getPort());
                    break;


                // Update i-th finger with node p
                case UPDATE_FINGER_TABLE:
                    Object[] msgObjArray = (Object[]) messageArray[1];
                    int i = (int) msgObjArray[0];
                    Node n = (Node) msgObjArray[1];
                    System.out.println(myNode.getNodeName() + "-SERVER: Updating " + i + "-th finger with new node entry: " + n.getNodeName() + ", " + n.getAddress().getAddress().getHostAddress() + ":" + n.getAddress().getPort());
                    myNode.updateFingerTable(i, n);
                    myNode.getFingerTable().printFingerTable();
                    response = MessageType.GOT_IT;
                    break;


                case PRINT_YOUR_FINGER_TABLE:
                    myNode.getFingerTable().printFingerTable();
                    response = MessageType.OK;
                    break;


                // Check if sharedFile id belongs to a sharedFile in the network
                case DOES_FILE_ID_EXIST:
                    Pair<Long, String> filePair = (Pair<Long, String>) messageArray[1];
                    response = MessageType.NOT_EXIST;
                    System.out.println(myNode.getNodeName() + " - SERVER: Checking if FILE ID exists: " + filePair.getKey() + ", title: "  + filePair.getValue());
                    response = myNode.checkIfFileIdExists(filePair.getKey(), filePair.getValue());
                    break;


                // Find node responsible for this sharedFile
                case FIND_FILE_SUCCESSOR:
                    id = (long) messageArray[1];
                    response = null;
                    System.out.println(myNode.getNodeName() + " - SERVER: Find FILE's successor: " + id);
                    response = myNode.findFileSuccessor(id);
                    break;


                // This sharedFile is assigned to me
                case THIS_FILE_BELONGS_TO_YOU:
                    SharedFile sharedFile = (SharedFile) messageArray[1];
                    myNode.getSharedFileList().add(sharedFile);
                    System.out.println(myNode.getNodeName() + " - SERVER: New SharedFile belongs to me: " + sharedFile.getId() + ", " + sharedFile.getTitle());
                    response = MessageType.GOT_IT;
                    break;


                // Transfer some of my sharedFiles to my predecessor
                case TRANSFER_YOUR_FILES_TO_ME:
                    id = (long) messageArray[1];
                    List<SharedFile> myNewSharedFileList = new ArrayList();
                    List<SharedFile> returnSharedFileList = new ArrayList();

                    for (SharedFile b : myNode.getSharedFileList()) {
                        // If sharedFile id is less than or equal my predecessor
                        if (Utils.isIdBetweenUpperEq(b.getId(), myNode.getNodeId(), id)) {
                            returnSharedFileList.add(b);
                        } else {
                            myNewSharedFileList.add(b);
                        }
                    }
                    myNode.setSharedFileList(myNewSharedFileList);
                    System.out.println(myNode.getNodeName() + " - SERVER: Transfer sharedFiles - my new sharedFile list: " + myNewSharedFileList);
                    System.out.println(myNode.getNodeName() + " - SERVER: Transfer sharedFiles - return sharedFile list: " + returnSharedFileList + " " + returnSharedFileList.size());
                    response = returnSharedFileList;
                    break;


                // new sharedFiles are assigned to me
                case YOU_HAVE_NEW_FILES:
                    List<SharedFile> sharedFiles = (List<SharedFile>) messageArray[1];
                    for (SharedFile b : sharedFiles) {
                        myNode.getSharedFileList().add(b);
                    }
                    response = MessageType.GOT_IT;
                    break;


                // Find a sharedFile in the network
                case FIND_FILE:
                    Pair<Long, String> searchFile = (Pair<Long, String>) messageArray[1];
                    List<SharedFile> results = myNode.findFileById(searchFile);
                    response = results;
                    break;


                // My predecessor wants to check if I'm alive.
                // Return my list of sharedFiles that are assigned to me.
                case ARE_YOU_STILL_ALIVE:
//                    System.out.println(myNode.getNodeName() + " - SERVER: I'm still alive.");
                    response = myNode.getSharedFileList();
                    break;


                // My predecessor want me to return my shared sharedFiles.
                // Return my the sharedFiles that I've shared with the network.
                case GIVE_YOUR_SHARED_FILES:
                    response = myNode.getmySharedFiles();
                    break;


                // Remove the shared sharedFile containing sharedFile's id and sharedFile's title
                case REMOVE_SHARED_FILE:
                    Pair<Long, String> removeFile = (Pair<Long, String>) messageArray[1];
                    myNode.removeSharedFile(removeFile);
                    response = MessageType.OK;
                    break;


                // check if sharedFile location is still available
                case IS_FILE_AVAILABLE:
                    String loc = (String) messageArray[1];
                    System.out.println(myNode.getNodeName() + " - SERVER: Check if sharedFile location is still available");
                    File file = new File(loc);
                    if (file.exists()) {
                        response = MessageType.FILE_IS_AVAILABLE;
                    } else {
                        response = MessageType.FILE_NOT_AVAILABLE;
                    }
                    break;


                // a user wants to download sharedFile
                case DOWNLOAD_FILE:
                String fileLocation = (String) messageArray[1];
                System.out.println(myNode.getNodeName() + " - SERVER - DOWNLOAD FILE - Location received from socket: " + fileLocation);
                if (fileLocation == null) {fileLocation = "";}

                // Creating object to send file
                File fileFile = new File(fileLocation);
                byte[] array = new byte[(int) fileFile.length()];
                FileInputStream fileInputStream = new FileInputStream(fileFile);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                bufferedInputStream.read(array, 0, array.length); // copied file into byteArray

                // Sending file size through socket
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(array.length);
                out.flush();

                // Sending file through socket
                OutputStream outputStream = socket.getOutputStream();
                System.out.println("Sending " + fileLocation + "( size: " + array.length + " bytes)");
                outputStream.write(array, 0, array.length);            //copying byteArray to socket
                outputStream.flush();                                        //flushing socket
                System.out.println("Done.");                                //file has been sent

                fileInputStream.close();
                bufferedInputStream.close();
                out.close();
                outputStream.close();

                break;
            }
            return response;
        } catch (Exception e){
            e.printStackTrace();
            return response;
        }
    }
}