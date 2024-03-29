package App.RunnableThread;

import App.Components.*;
import javafx.util.Pair;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * This thread will periodically verify my immediate successor
 * and tell the successor about me.
 */
public class Stabilize implements Runnable, Serializable {

    private final int periodTime = 100;
    private Node myNode = null;
    private static boolean isRunning = true;
    private int tryTimes = 0;

    public Stabilize(Node node) {
        try {
            this.myNode = node;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            System.out.println("Stabilize running");
            Object[] objArray = new Object[1];
            while (isRunning) {

                Socket socket = new Socket();
                Node mySuc = myNode.getSuccessor();
                try {
                    if (mySuc.getNodeId() != this.myNode.getNodeId()) {
//                        System.out.println("Connecting to " + mySuc.getAddress().getAddress().getHostAddress() + ":" + myNode.getSuccessor().getAddress().getPort());

                        /*
                         * Check if my successor is still alive
                         */
                        // Send message to my successor to check if it's still alive
                        socket.connect(mySuc.getAddress(), 3000);
                        objArray[0] = MessageType.ARE_YOU_STILL_ALIVE;
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                        objectOutputStream.writeObject(objArray);
                        objectOutputStream.flush();

                        // Wait 50 millisecs to receive the response
                        Thread.sleep(50);

                        // Receive response suc's file list, which contains files assigned to my suc
                        ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                        List<SharedFile> response = (List<SharedFile>) objectInputStream.readObject();
                        myNode.setMySucSharedFileList(response);

                        objectInputStream.close();
                        objectOutputStream.close();
                        socket.close();

                        tryTimes = 0;

                        /*
                         * Stabilization protocol
                         */
                        objArray[0] = MessageType.GET_YOUR_PREDECESSOR;
                        Node pre = (Node) Utils.sendMessage(myNode.getSuccessor().getAddress(), objArray);

                        // If pre is between me and my successor, pre should be my successor
                        if (Utils.isIdBetweenNotEq(pre.getNodeId(), myNode.getNodeId(), myNode.getSuccessor().getNodeId())) {
                            System.out.println(myNode.getNodeName() + " - STABILIZE - found my new successor: " + pre.getNodeName());
                            myNode.setSuccessor(pre);

                            // Update my 1st entry in the finger table
                            myNode.getFingerTable().updateEntryNode(1, pre);
                        }

                        // Notify my new successor that I'm its new predecessor
                        if (pre.getNodeId() != myNode.getNodeId()) {
                            System.out.println(myNode.getNodeName() + " - STABILIZE - My successor has changed! Notify my new successor...");
                            myNode.notifyMyNewSuccessor(pre.getAddress());

                            /*
                             * Get my successor's shared files
                             */
                            objArray[0] = MessageType.GIVE_YOUR_SHARED_FILES;
                            List<Pair<Long, String>> sharedFiles = (List<Pair<Long, String>>) Utils.sendMessage(mySuc.getAddress(), objArray);
                            myNode.setMySucSharedFiles(sharedFiles);

                            /*
                             * Get my successor's successor
                             */
                            objArray[0] = MessageType.GET_YOUR_SUCCESSOR;
                            Node sucSuc = (Node) Utils.sendMessage(mySuc.getAddress(), objArray);
                            if (sucSuc != null) {
//                            System.out.println(myNode.getNodeName() + " - STABILIZE - Found successor of my successor: " + sucSuc.getNodeName());
                                this.myNode.setSucSuccessor(sucSuc);
                            }
                        }

                    } else {
                        this.myNode.setSucSuccessor(this.myNode);
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("SocketTimeoutException");
                    if (tryTimes >= 2) {
                        handleFailedSuccessor(mySuc);
                        tryTimes = 0;
                    } else {
                        tryTimes ++;
                    }
                } catch (ConnectException | NullPointerException e) {
                    e.printStackTrace();
                    handleFailedSuccessor(mySuc);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Thread.sleep(periodTime);
            }
            System.out.println("Stabilize closing");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleFailedSuccessor(Node mySuc) {
        try {
            /*
             * My successor is no longer available
             */
            System.out.println(myNode.getNodeName() + " - STABILIZE - My successor is no longer available: " + mySuc.getNodeName());

            // Remove all my successor's shared files
            System.out.println(myNode.getNodeName() + " - STABILIZE - Removing my successor's share files: " + myNode.getMySucSharedFiles().size());
            for (Pair<Long, String> file : myNode.getMySucSharedFiles()) {
                String titleAddress = file.getValue() + myNode.getSuccessor().getAddress().getAddress().getHostAddress() + ":" + myNode.getSuccessor().getAddress().getPort();
                System.out.println(myNode.getNodeName() + " - STABILIZE - Remove shared file: " + file.getKey() + ", " + titleAddress);
                myNode.removeSharedFile(new Pair<Long, String>(file.getKey(), titleAddress));
            }

            // Find my new successor
            System.out.println(myNode.getNodeName() + " - STABILIZE - My successor left the network:" + mySuc.getNodeName());
            Node newSuc = myNode.getSucSuccessor();
            System.out.println(myNode.getNodeName() + " - STABILIZE - My successor left the network! Found my new successor: " + newSuc.getNodeName());
            myNode.setSuccessor(newSuc);
            myNode.getFingerTable().updateEntryNode(1, newSuc);

            // Remove files shared by my old successor and my old successor's holding them
            List<SharedFile> myOldSucSharedFileList = new ArrayList();
            for (SharedFile b : myNode.getMySucSharedFileList()) {
                // If this file is not shared by my old successor, move it to my new successor
                if (!b.getOwnerAddress().getAddress().getHostAddress().equals(mySuc.getAddress().getAddress().getHostAddress()) && b.getOwnerAddress().getPort() != mySuc.getAddress().getPort()) {
                    myOldSucSharedFileList.add(b);
                }
            }

            // Notify my new successor that I'm its new predecessor
            if (newSuc.getNodeId() == myNode.getNodeId()) { // I'm the only user left in the network
                myNode.setSuccessor(myNode);
                myNode.setPredecessor(myNode);
                myNode.setFingerTable(new FingerTable(myNode));
            } else {
                MessageType response = myNode.notifyMyNewSuccessor(newSuc.getAddress());
            }

            if (newSuc.getNodeId() == myNode.getNodeId()) { // I'm the only user left in the network
                List<SharedFile> mySharedFileList = myNode.getSharedFileList();
                for (SharedFile b: myOldSucSharedFileList) {
                    if (b.getOwnerAddress().getAddress().getHostAddress().equals(myNode.getAddress().getAddress().getHostAddress()) && b.getOwnerAddress().getPort() == myNode.getAddress().getPort()) {
                        mySharedFileList.add(b);
                    }
                }
            } else {
                // And transfer files from my old successor to my new successor
                System.out.println(myNode.getNodeName() + " - STABILIZE - My successor left the network! Transferring old successor's files to new successor");
                Object[] objArray = new Object[2];
                objArray[0] = MessageType.YOU_HAVE_NEW_FILES;
                objArray[1] = myOldSucSharedFileList;
                MessageType response = (MessageType) Utils.sendMessage(myNode.getSuccessor().getAddress(), objArray);
                if (response == MessageType.GOT_IT) {
                    System.out.println(myNode.getNodeName() + " - STABILIZE - Successfully transferred files to new successor");
                } else {
                    System.out.println(myNode.getNodeName() + " - STABILIZE - Failed to transfer files to new successor");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeStabilize() {
        isRunning = false;
    }

}
