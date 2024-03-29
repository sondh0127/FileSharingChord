package App.Components;

import App.RunnableThread.FixFingers;
import App.RunnableThread.Listener;
import App.RunnableThread.Stabilize;
import javafx.util.Pair;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class Node implements Serializable {

    private static int m = 7;
    private static long chordRingSize = (long) Math.pow(2, m);

    private InetSocketAddress address = null;
    private Node predecessor = null;
    private Node successor = null;
    private Node sucSuccessor = null;
    private FingerTable fingerTable = null;

    private List<SharedFile> sharedFileList = null;
    private List<SharedFile> mySucSharedFileList = null;
    private List<Pair<Long, String>> mySharedFiles = null;
    private List<Pair<Long, String>> mySucSharedFiles = null;

    private String addressString = "";
    private long nodeId = -1;
    private String nodeName = "";

    private Listener listener;
    private Stabilize stabilize;
    private FixFingers fixFingers;

    public Node(InetSocketAddress address) {
        try {
            this.address = address;

            // Initialize nodeId and nodeName
            addressString = address.getAddress().getHostAddress() + ":" + address.getPort();
            generateIdAndName(addressString);

            // Initialize a finger table
            fingerTable = new FingerTable(this);

            // Initialize list of files
            sharedFileList = new ArrayList();
            mySucSharedFileList = new ArrayList();
            mySharedFiles = new ArrayList();
            mySucSharedFiles = new ArrayList();

            // initialize threads
            listener = new Listener(this);
            stabilize = new Stabilize(this);
            fixFingers = new FixFingers(this);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // For testing
    public Node(InetSocketAddress address, long nodeId, String nodeName) {
        try {
            this.address = address;
            this.nodeId = nodeId;
            this.nodeName = nodeName;

            // Initialize a finger table
            fingerTable = new FingerTable(this);

            // Initialize list of files
            sharedFileList = new ArrayList();
            mySucSharedFileList = new ArrayList();
            mySharedFiles = new ArrayList();
            mySucSharedFiles = new ArrayList();

            // initialize threads
            listener = new Listener(this);
            stabilize = new Stabilize(this);
            fixFingers = new FixFingers(this);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * generate new hash key for node's ID and node's name from given address string
     * @param addressString
     */
    private void generateIdAndName(String addressString) {
        try {
            long hashValue = Utils.hashFunction(addressString);
            nodeId = hashValue;
            nodeName = "N" + nodeId;
            System.out.println("nodeId: " + nodeId);
            System.out.println("nodeName: " + nodeName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Start listener thread, stabilize thread, and fixFingers thread
     */
    private void startThreads() {
        try {
            Thread listenerThread = new Thread(listener);
            Thread stabilizeThread = new Thread(stabilize);
            Thread fixFingersThread = new Thread(fixFingers);

            listenerThread.start();
            stabilizeThread.start();
            fixFingersThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create new Chord network
     * @return true if successfully creating new network. Otherwise, false
     */
    public boolean createNewNetwork() {
        try {
            System.out.println("Creating new network with 1 node (ID=" + nodeId + ") , network's size= " + getChordRingSize() + "...");

            this.successor = this;
            this.predecessor = this;

            startThreads();

            System.out.println("Created network. My nodeId=" + nodeId + ", nodeName=" + nodeName);
            this.fingerTable.printFingerTable();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Join the ring in the Chord network from the given contact address
     * @param contactAddress
     * @return true if successfully joining the network. Otherwise, false
     */
    public boolean joinNetwork(InetSocketAddress contactAddress) {
        try {
//            System.out.println("join Network from contact " + contactAddress.getAddress().getHostAddress() + ":" + contactAddress.getPort());
//            System.out.println("My address: " + address.getAddress().getHostAddress() + ":" + address.getPort());
            if (contactAddress == null || (contactAddress.getAddress().getHostAddress().equals(address.getAddress().getHostAddress()) && contactAddress.getPort() == address.getPort())) {
                System.out.println("Can't join my own address");
                return false;
            }

            // Check if nodeId already exists in the network
            // If nodeId already exists, continue generating and checking new nodeId until nodeId doesn't exist
            Object[] objArray = new Object[2];
            objArray[0] = MessageType.DOES_ID_EXIST;
            objArray[1] = this.nodeId;
            while(Utils.sendMessage(contactAddress, objArray) == MessageType.ALREADY_EXIST) {
                System.out.println("ID " + nodeId + "already exists, generating new ID...");
                addressString += ".";
                generateIdAndName(addressString);
                objArray[1] = this.nodeId;
            }

            /*
             * Join the network
             */
            // Initialize finger table of local node
            boolean status = initFingerTable(contactAddress);
            if (status == false) {return false;}

            // Update all nodes whose finger tables should refer to local node
            status = updateFingerTableOfOtherNodes();
            if (status == false) {return false;}

            // Transferring files to me
            // Contact my successor to transfer some files to me
            List<SharedFile> mySharedFileList = transferFilesToMe();
            this.setSharedFileList(mySharedFileList);
            System.out.println("My SharedFile list: " + mySharedFileList.size());

            // Start the threads for this node
            startThreads();

            objArray[0] = MessageType.PRINT_YOUR_FINGER_TABLE;
            Utils.sendMessage(contactAddress, objArray);
            this.fingerTable.printFingerTable();
            System.out.println("\n" + nodeName + " joined network from contact " + contactAddress.getAddress().getHostAddress() + ":" + contactAddress.getPort());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initialize the successor, predecessor and fingers of node.
     * Ask contact, node in the network, to initialize finger table of local node
     * @param contactAddress
     * @return true if successfully initialized finger table. Otherwise, false.
     */
    private boolean initFingerTable(InetSocketAddress contactAddress) {
        try {
            System.out.println("**************************************************************");
            Object[] objArray = new Object[2];

            /*
             * Ask contact to find the entry node of the 1st finger
             * This entry node is also my successor
             */
            objArray[0] = MessageType.FIND_SUCCESSOR;
            objArray[1] = this.fingerTable.getRange(1).getKey();
            System.out.println(nodeName + " - INIT.FINGER.TABLE - FIND SUC: asking contact " + contactAddress.getAddress().getHostAddress() + ":" + contactAddress.getPort() +
                                " to find successor of id=" + objArray[1]);
            this.successor = (Node) Utils.sendMessage(contactAddress, objArray);
            if (this.successor == null) { return false; }
            this.fingerTable.updateEntryNode(1, this.successor);
            System.out.println(nodeName + " - INIT.FINGER.TABLE - FOUND SUCCESSOR OF me and 1st finger: " + this.successor.getNodeName());

            /*
             * Get my successor's successor
             */
            objArray[0] = MessageType.GET_YOUR_SUCCESSOR;
            System.out.println(nodeName + " - INIT.FINGER.TABLE - GET SUC's SUC: asking successor " + this.getSuccessor().getAddress().getAddress().getHostAddress() + ":" + this.getSuccessor().getAddress().getPort() +
                    " to get its successor");
            this.sucSuccessor = (Node) Utils.sendMessage(contactAddress, objArray);
            if (this.sucSuccessor == null) { return false; }
            System.out.println(nodeName + " - INIT.FINGER.TABLE - Found successor of my successor: " + this.sucSuccessor.getNodeName());


            /*
             * Get predecessor of my successor, it's now my predecessor
             */
            objArray[0] = MessageType.GET_YOUR_PREDECESSOR;
            System.out.println(nodeName + " - INIT.FINGER.TABLE - GET PRE: asking contact " + contactAddress.getAddress().getHostAddress() + ":" + contactAddress.getPort() +
                                " to get its predecessor");
            this.predecessor = (Node) Utils.sendMessage(contactAddress, objArray);
            if (this.predecessor == null) { return false; }
            System.out.println(nodeName + " - INIT.FINGER.TABLE - FOUND PREDECESSOR OF me: " + this.predecessor.getNodeName());

            /*
             * Notify my new successor that I'm its new predecessor
             */
            System.out.println(nodeName + " - INIT.FINGER.TABLE: notify my new successor "  + this.successor.getNodeName()  + " - " + this.successor.getAddress().getAddress().getHostAddress() + ":" + this.successor.getAddress().getPort() +
                                " that I'm the new predecessor, id=" + this.nodeName);
            MessageType response = notifyMyNewSuccessor(this.getSuccessor().getAddress());
            if (response != MessageType.GOT_IT) { return false; }

            /*
             * Notify my new predecessor that I'm its new successor
             */
            System.out.println(nodeName + " - INIT.FINGER.TABLE: notify my new predecessor " + this.predecessor.getNodeName()  + " - " + this.predecessor.getAddress().getAddress().getHostAddress() + ":" + this.predecessor.getAddress().getPort() +
                    " that I'm the new successor, id=" + this.nodeName);
            response = notifyMyNewPredecessor(this.getPredecessor().getAddress());
            if (response != MessageType.GOT_IT) { return false; }

            /*
             * Update other entries in the finger table
             */
            for (int i=1; i < getM(); i++) {
//                this.fingerTable.printFingerTable();
                // If (i+1)-th finger's start position is between this node and entry node of i-th finger,
                // (i+1)-th finger entry is the successor of this node.
                long iplus1_start = this.fingerTable.getRange(i+1).getKey();
                System.out.println(nodeName + ": INIT.FINGER.TABLE: Updating " + (i+1) + "-th finger entry, ask contact " + contactAddress.getAddress().getHostAddress() + ":" + contactAddress.getPort() +
                        " to find successor of id=" +iplus1_start);
                //if (iplus1_start >= this.nodeId && iplus1_start < this.fingerTable.getEntryNode(i).getNodeId()) {
//                if (isIdBetweenLowerEq(iplus1_start, this.nodeId, this.fingerTable.getEntryNode(i).getNodeId())) {
                if (Utils.isIdBetweenEq(iplus1_start, this.nodeId, this.fingerTable.getEntryNode(i).getNodeId())) {
                    this.fingerTable.updateEntryNode(i+1, this.fingerTable.getEntryNode(i));

                // Otherwise, (i+1)-th finger entry is the successor of (i+1)-th finger's start position
                } else {
                    // Ask contact to find successor of (i+1)-th finger's start position
                    objArray[0] = MessageType.FIND_SUCCESSOR;
                    objArray[1] = iplus1_start;
                    Node n = (Node) Utils.sendMessage(contactAddress, objArray);

                    // Update (i+1)-th finger entry
                    this.fingerTable.updateEntryNode(i+1, n);
                }
            }
            System.out.println(nodeName + " - Successfully initialized finger table!\n");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update the finger tables and predecessors of existing nodes to reflect the addition of this new local node
     *
     * This local node will become the i-th finger of a node p if and only if
     * (1) p precedes this local node by at least 2^(i-1), and
     * (2) the i-th finger of node p succeeds this local node.
     *
     * @return true if successfully update other nodes. Otherwise, false.
     */
    private boolean updateFingerTableOfOtherNodes() {
        try {
            System.out.println("**************************************************************");
            this.fingerTable.printFingerTable();
            for (int i=1; i <= getM(); i++) {
                System.out.println();
                // find last node p whose i-th finger might be this local node
                long id = (this.nodeId - (long) Math.pow(2, i-1)) % chordRingSize;

                System.out.println(nodeName + "- UPDATE.OTHER.NODES: i= " + i + ", last node id= " + id);
                Node p = findPredecessorOf(id);
                if (p != null && p.getNodeId() != this.nodeId) {
                    // Update i-th finger in the finger table of p
                    Object[] msgArray = new Object[2];
                    msgArray[0] = i;
                    msgArray[1] = this;
                    System.out.println(nodeName + "- UPDATE.OTHER.NODES: Updating " + i + "-th finger of node " +
                            p.getNodeName() + ", " + p.getAddress().getAddress().getHostAddress() + ":" + p.getAddress().getPort() +
                            " with node " + this.nodeName + ", " + this.getAddress().getAddress().getHostAddress() + ":" + this.getAddress().getPort());
                    MessageType response = sendUpdateFingerTableMessage(p.getAddress(), msgArray);
                    if (response != MessageType.GOT_IT) { return false;}
                }
            }
            System.out.println(nodeName + " - Successfully updated finger table of other nodes!\n");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * if  n is i-th finger of this local node, update local node’s finger table with n
     * @param i
     * @param n
     */
    public void updateFingerTable(int i, Node n) {
        try {
            //if (n.getNodeId() >= this.nodeId && n.getNodeId() < this.fingerTable.getEntryNode(i).getNodeId()) {
            if (Utils.isIdBetweenLowerEq(n.getNodeId(), this.nodeId, this.fingerTable.getEntryNode(i).getNodeId())) {
                System.out.println(nodeName + " - UPDATE.FINGER.TABLE - Updating " + i + "-th finger with new node: " + n.getNodeName());
                this.fingerTable.updateEntryNode(i, n);
                // If my 1-st finger needs to be updated, my successor needs to be updated as well
                if (i == 1) {
                    this.successor = n;
                }

                Node p = this.predecessor; // get first preceding node
                if (p.getNodeId() != n.getNodeId()) {
                    // tell predecessor p to update finger table
                    // Update i-th finger in the finger table of p
                    // p.updateFingerTable(i, n);
                    Object[] msgArray = new Object[2];
                    msgArray[0] = i;
                    msgArray[1] = n;
                    System.out.println(nodeName + " - UPDATE.FINGER.TABLE: Tell to Update " + i + "-th finger of node " +
                            p.getNodeName() + ", " + p.getAddress().getAddress().getHostAddress() + ":" + p.getAddress().getPort() +
                            " with node " + n.nodeName + ", " + n.getAddress().getAddress().getHostAddress() + ":" + n.getAddress().getPort());
                    sendUpdateFingerTableMessage(p.getAddress(), msgArray);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private MessageType sendUpdateFingerTableMessage(InetSocketAddress address, Object[] msgArray) {
        try {
            Object[] objArray = new Object[2];
            objArray[0] = MessageType.UPDATE_FINGER_TABLE;
            objArray[1] = msgArray;
            return (MessageType) Utils.sendMessage(address, objArray);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Ask current node to find ID's successor.
     * @param ID
     * @return ID's successor node
     *
     */
    public Node findSuccessorOf(long ID) {
        try {
            if (ID < 0 || ID > chordRingSize) { return null; }

            if (this.nodeId == ID) {
//                System.out.println(nodeName + ": FINDING SUCCESSOR OF " + ID + ", which is me, is my successor, " + this.successor.getNodeName());
                return this.successor;
            }

            Node successor = this.successor;
            // Find predecessor of ID
            Node predecessor = findPredecessorOf(ID);

            // Find successor of predecessor of ID
            // Successor of ID = successor of predecessor of ID
            if (predecessor != null) {
//                System.out.println(nodeName + ": FINDING SUCCESSOR OF " + ID + " - FOUND ID's PRE " + predecessor.getNodeName());

                if (predecessor.getNodeId() == this.nodeId) {
                    successor = this.successor;
                } else {
                    Object[] objArray = new Object[1];
                    objArray[0] = MessageType.GET_YOUR_SUCCESSOR;
                    successor = (Node) Utils.sendMessage(predecessor.getAddress(), objArray);
                }
            }

//            System.out.println(nodeName + ": FIND SUCCESSOR OF " + ID + " - FOUND " + successor.getNodeName());
            return successor;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Ask current node to find ID's predecessor.
     * @param ID
     * @return ID's predecessor node
     */
    private Node findPredecessorOf(long ID) {
        try {
            if (ID < 0 || ID > chordRingSize) { return null; }

//            System.out.println(nodeName + ": FINDING PREDECESSOR OF " + ID + "...");
            if (this.nodeId == ID) {
//                System.out.println(nodeName + ": FINDING PREDECESSOR OF " + ID + ", which is me, is my predecessor, " + this.predecessor.getNodeName());
                return this.predecessor;
            }

            // Start with myself
            Node n = this;

            // ID is not between n and n's successor
            if (n.getSuccessor() != null) {
                while( !Utils.isIdBetweenUpperEq(ID, n.getNodeId(), n.getSuccessor().getNodeId()) ) {
//                    System.out.println(nodeName + ": FINDING PREDECESSOR OF id=" + ID + ", n.getNodeId()=" + n.getNodeId() + ", n.getSuccessor().getNodeId()=" + n.getSuccessor().getNodeId());
                    if (n == null) {
//                        System.out.println(nodeName + ": FIND PREDECESSOR: Node is null, can't find ID's predecessor");
                        return null;
                    }
                    if (n.getNodeId() == n.getSuccessor().getNodeId()) {
//                        System.out.println(nodeName + ": FINDING PREDECESSOR OF id=" + ID + ", n is the successor of itself");
                        break;
                    }
//                    if (this.fingerTable.findIthFingerOf(ID) >= this.fingerTable.getTransitionFinger()) {
//                        System.out.println(nodeName + ": FINDING PREDECESSOR OF id=" + ID + ", ID is in the finger over transition finger");
////                        if (n.getSuccessor().getNodeId() == n.getPredecessor().getNodeId()) {
////                            n = n.getSuccessor();
////                        }
//                        break;
//                    }

                    // Ask n to find closest finger preceding ID
                    if (n.getNodeId() == this.nodeId) {
                        n = closestPrecedingFingerOf(ID);
                    } else {
                        Object[] objArray = new Object[2];
                        objArray[0] = MessageType.CLOSEST_PRECEDING_FINGER;
                        objArray[1] = ID;
                        n = (Node) Utils.sendMessage(n.getAddress(), objArray);
                    }
                }
            }
//            System.out.println(nodeName + ": FIND PREDECESSOR: FOUND ID=" + ID + " 's predecessor: " + n.getNodeName());
            return n;
        } catch (Exception e) {
            e.printStackTrace();
            return  null;
        }
    }

    /**
     * Find closest finger preceding ID
     * @param ID
     * @return closest finger preceding ID
     */
    public Node closestPrecedingFingerOf(long ID) {
        try {
            if (ID < 0 || ID > chordRingSize) { return null; }
//            System.out.println(nodeName + "- FINDING CLOSEST.PRECEDING.FINGER.OF " + ID);
//            this.fingerTable.printFingerTable();
            for (int i = Node.getM(); i > 1; i--) {
                Node entryNode = fingerTable.getEntryNode(i);
//                System.out.println(nodeName + "- CLOSEST.PRECEDING.FINGER.OF " + ID + ", i-th finger=" + i + " , entryNode=" + entryNode.getNodeName());
                if (entryNode != null) {
                    if (Utils.isIdBetweenNotEq(entryNode.getNodeId(), this.nodeId, ID)) {
                        Node returnNode = fingerTable.getEntryNode(i);
//                        System.out.println(nodeName + "- CLOSEST.PRECEDING.FINGER.OF " + ID + " is " + returnNode.getNodeName() + ", " + returnNode.getAddress().getAddress().getHostAddress() + ":" + returnNode.getAddress().getPort());
                        return returnNode;
                    }
                }
            }
//            System.out.println(nodeName + "- CLOSEST.PRECEDING.FINGER.OF " + ID + " is " + nodeName + ", " + address.getAddress().getHostAddress() + ":" + address.getPort());
            return  this;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    /**
     * Notify my new successor that I'm its new predecessor
     * @param successorAddress
     * @return successor's response
     */
    public MessageType notifyMyNewSuccessor(InetSocketAddress successorAddress) {
        try {
            if (successorAddress == null || (successorAddress.getAddress().getHostAddress().equals(address.getAddress().getHostAddress()) && successorAddress.getPort() == address.getPort())) {
                System.out.println(nodeName + ": Can't notify! empty successor information or I'm my successor");
                return null;
            } else {
                System.out.println(nodeName + ": Notifying my new successor (" + successorAddress.getAddress().getHostAddress() + ":" + successorAddress.getPort() + ") that I'm (" + address.getAddress().getHostAddress() + ":" + address.getPort() + ") its new predecessor: ");
                Object[] objArray = new Object[2];
                objArray[0] = MessageType.I_AM_YOUR_NEW_PREDECESSOR;
                objArray[1] = this;
                return (MessageType) Utils.sendMessage(successorAddress, objArray);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Notify my new predecessor that I'm its new successor
     * @param predecessorAddress
     * @return predecessor's response
     */
    public MessageType notifyMyNewPredecessor(InetSocketAddress predecessorAddress) {
        try {
            if (predecessorAddress == null || (predecessorAddress.getAddress().getHostAddress().equals(address.getAddress().getHostAddress()) && predecessorAddress.getPort() == address.getPort())) {
                System.out.println(nodeName + " - Can't notify! empty successor information or I'm my predecessor");
                return null;
            } else {
                System.out.println(nodeName + ": Notifying my new predecessor (" + predecessorAddress.getAddress().getHostAddress() + ":" + predecessorAddress.getPort() + ") that I'm " + this.nodeName + " (" + address.getAddress().getHostAddress() + ":" + address.getPort() + ") its new successor: ");
                Object[] objArray = new Object[2];
                objArray[0] = MessageType.I_AM_YOUR_NEW_SUCCESSOR;
                objArray[1] = this;
                return (MessageType) Utils.sendMessage(predecessorAddress, objArray);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Check if given id already belongs to a node/user in the network
     * @param id
     * @return True if it belongs to a node/user. Otherwise, False
     */
    public MessageType checkIfIdExists(long id) {
        try {
            MessageType response = MessageType.NOT_EXIST;

            // Check my nodeID
            if (id == this.getNodeId()) {
                System.out.println(this.getNodeName() + " - CHECK.IF.ID.EXISTS: ID belongs to me");
                return MessageType.ALREADY_EXIST;
            }

            // Check my successor and predecessor
            if ((this.getSuccessor() != null && id == this.getSuccessor().getNodeId()) || (this.getPredecessor() != null && id == this.getPredecessor().getNodeId())) {
                System.out.println(this.getNodeName() + " - CHECK.IF.ID.EXISTS: ID belongs to my predecessor or successor");
                return MessageType.ALREADY_EXIST;
            }

            // Check the finger table
            System.out.println(this.getNodeName() + " - CHECK.IF.ID.EXISTS: Checking finger table");
            FingerTable fingerTable = this.getFingerTable();
            int iThFinger = fingerTable.findIthFingerOf(id); // Find the finger that stores information of the node ID
            if (iThFinger == 0) {
                System.out.println(this.getNodeName() + " - CHECK.IF.ID.EXISTS: ID " + id + " is too large");
                return MessageType.ALREADY_EXIST;
            }

            System.out.println(this.getNodeName() + " - CHECK.IF.ID.EXISTS: Found ID in the finger # " + iThFinger);
            fingerTable.printFingerTable();
            // If ID is in the 1st finger and if ID exists, ID must be my successor.
            if (iThFinger == 1 && this.getSuccessor().getNodeId() != id) {
                System.out.println(this.getNodeName() + " - CHECK.IF.ID.EXISTS: ID is not my successor, so it doesn't exist");
                return MessageType.NOT_EXIST;
            }
            Node contact = fingerTable.getEntryNode(iThFinger);
            if (contact != null && contact.getNodeId() != this.getNodeId()) {   // If ID is already assigned to a node which is not me
                // If contact precedes the ID, contact won't have information of that ID. ID doesn't exist
                if (Utils.isIdBetweenUpperEq(id, this.getNodeId(), contact.getNodeId())) {
                    return MessageType.NOT_EXIST;
                } else {
                    // Contact that node to see if ID belongs to another node
                    System.out.println(this.getNodeName() + " - CHECK.IF.ID.EXISTS: Contacting node #" +  contact.getNodeId() +
                                        " (" + contact.getAddress().getAddress().getHostAddress() + ":" + contact.getAddress().getPort() + ")...");
                    Object[] objArray = new Object[2];
                    objArray[0] = MessageType.DOES_ID_EXIST;
                    objArray[1] = id;
                    response = (MessageType) Utils.sendMessage(contact.getAddress(), objArray);
                }
            }

            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return MessageType.ALREADY_EXIST;
        }
    }


    /**
     * Share a file with its information with the network including:
     * Assign it an id
     * Assign it to a node
     * @param title
     * @param author
     * @param location
     * @return SharedFile that is successfully shared. Otherwise, null
     */
    public SharedFile shareAFile(String title, String author, String location) {
        try {

            // Remove all non-alphanumeric characters
            String fileString = title.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
            System.out.println(nodeName + " - SHARE.NEW.FILE - filestring= " + fileString);
            long id = Utils.hashFunction(fileString);

            while(checkIfFileIdExists(id, fileString) == MessageType.ALREADY_EXIST) {
                System.out.println("ID " + id + "already exists, generating new ID...");
                fileString += ".";
                id = Utils.hashFunction(fileString);
            }
            System.out.println(nodeName + " - SHARE.NEW.FILE - new file: " + title + " (" + location + ") : id=" + id);
            SharedFile newSharedFile = new SharedFile(id, this.address, title, author, location, true);

            if (newSharedFile.getId() == this.getNodeId()) {
                // This file is assigned to me
                System.out.println(nodeName + " - SHARE.NEW.FILE - This file belongs to me: file id=" + id);
                this.sharedFileList.add(newSharedFile);
                return newSharedFile;
            } else {
                // Find the node responsible for this file
                System.out.println(nodeName + " - SHARE.NEW.FILE - find file's successor: file id=" + id);
                InetSocketAddress contact = findFileSuccessor(id);
                if (contact == null) { return null; }

                // send file to the appropriate node responsible for it
                Object[] objArray = new Object[2];
                objArray[0] = MessageType.THIS_FILE_BELONGS_TO_YOU;
                objArray[1] = newSharedFile;
                MessageType response = (MessageType) Utils.sendMessage(contact, objArray);
                if (response == MessageType.GOT_IT) {
                    return newSharedFile;
                } else {
                    return null;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Find the successor of the file. In other words, find the node/user responsible for this file
     * @param id
     * @return address of the node/user responsible for this file
     */
    public InetSocketAddress findFileSuccessor(long id) {
        try {
            // Check my nodeID
            if (this.getPredecessor().getNodeId() < id && id <= this.getNodeId()) {
                System.out.println(this.getNodeName() + " - FIND.FILE.SUCCESSOR: SharedFile belongs to me");
                return this.address;
            // Check my finger table
            } else {
                FingerTable fingerTable = this.getFingerTable();
                int iThFinger = fingerTable.findIthFingerOf(id); // Find the finger that stores information of the SharedFile ID
                if (iThFinger == 0) {
                    System.out.println(this.getNodeName() + " - FIND.FILE.SUCCESSOR: ID " + id + " is too large");
                    return null;
                } else {
                    System.out.println(this.getNodeName() + " - FIND.FILE.SUCCESSOR:: Found FILE ID in the finger # " + iThFinger);
                    fingerTable.printFingerTable();

                    // Ask i-th entry node to check file id
                    Node contact = fingerTable.getEntryNode(iThFinger);
                    if (contact.getNodeId() == this.getNodeId()) {
                        // I'm file's successor
                        return this.getAddress();
                    } else if (Utils.isIdBetweenUpperEq(id, contact.getNodeId(), this.getNodeId())) {
                        // SharedFile id is in interval (contact, me], so I should hold that file if it exists. Otherwise, it doesn't exist
                        System.out.println(this.getNodeName() + " - FIND.FILE.SUCCESSOR: SharedFile Id belongs to me");
                        return this.address;
                    } else {
                        System.out.println(this.getNodeName() + " - FIND.FILE.SUCCESSOR: Contacting node #" + contact.getNodeId() + " (" + contact.getAddress().getAddress().getHostAddress() + ":" + contact.getAddress().getPort() + ")...");
                        Object[] objArray = new Object[2];
                        objArray[0] = MessageType.FIND_FILE_SUCCESSOR;
                        objArray[1] = id;
                        return (InetSocketAddress) Utils.sendMessage(contact.getAddress(), objArray);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Check if a given id already belongs to a different file, which has different title, shared by a user
     * @param id
     * @param title
     * @return True if it belongs to a file which has different title. Otherwise, False.
     */
    public MessageType checkIfFileIdExists(long id, String title) {
        try {
            MessageType response = MessageType.NOT_EXIST;
            System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: id=" + id + " , title="  + title);
            // Check my nodeID
            if (id == this.getNodeId()) {
                System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: ID belongs to me");
                // Check if id belongs to some file, which has different title, assigned to me
                for (SharedFile b : sharedFileList) {
                    if (b.getId() == id) {
                        if (b.getTitle().replaceAll("[^A-Za-z0-9]", "").toLowerCase().equals(title)) {
                            System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: SharedFile ID belongs to the same file " + b.getTitle());
                            return MessageType.NOT_EXIST;
                        } else {
                            System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: SharedFile ID currently belongs to a file " + b.getTitle());
                            return MessageType.ALREADY_EXIST;
                        }
                    }
                }
            // Check my finger table
            } else {
                FingerTable fingerTable = this.getFingerTable();
                int iThFinger = fingerTable.findIthFingerOf(id); // Find the finger that stores information of SharedFile node ID
                if (iThFinger == 0) {
                    System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: ID " + id + " is too large");
                    response = MessageType.ALREADY_EXIST;
                } else {
                    System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: Found FILE ID=" + id + " in the finger # " + iThFinger);
                    fingerTable.printFingerTable();

                    // Ask i-th entry node to check file id
                    Node contact = fingerTable.getEntryNode(iThFinger);
                    if ( (contact.getNodeId() == this.getNodeId()) || (contact.getNodeId() < id && id <= this.getNodeId()) ) {
                        // Check if id belongs to some file, which has different title, assigned to me
                        for (SharedFile b : sharedFileList) {
                            if (b.getId() == id) {
                                if (b.getTitle().replaceAll("[^A-Za-z0-9]", "").toLowerCase().equals(title)) {
                                    System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: FileID belongs to the same file " + b.getTitle());
                                    return MessageType.NOT_EXIST;
                                } else {
                                    System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: SharedFile ID currently belongs to a file " + b.getTitle());
                                    return MessageType.ALREADY_EXIST;
                                }
                            }
                        }
                    // If id is in interval (contact, me] and contact is my predecessor. I should hold this file
                    } else if (Utils.isIdBetweenUpperEq(id, contact.getNodeId(), this.getNodeId()) && this.getPredecessor().getNodeId() == contact.getNodeId()) {
                        System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: SharedFile Id should have belonged to me, no need to ask contact");
                    } else {
                        System.out.println(this.getNodeName() + " - CHECK.IF.FILE.ID.EXISTS: Contacting node #" + contact.getNodeId() + " (" + contact.getAddress().getAddress().getHostAddress() + ":" + contact.getAddress().getPort() + ")...");
                        Object[] objArray = new Object[2];
                        objArray[0] = MessageType.DOES_FILE_ID_EXIST;
                        Pair<Long, String> filePair = new Pair(id, title);
                        objArray[1] = filePair;
                        response = (MessageType) Utils.sendMessage(contact.getAddress(), objArray);
                    }
                }
            }
            System.out.println(this.getNodeName() + " - CHECK.IF.ID.EXISTS: Response=" + response);
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return MessageType.ALREADY_EXIST;
        }
    }


    /**
     * Tell my successor to give me the list of files, which are assigned to him.
     * @return list of files that are assigned to my successor
     */
    public List<SharedFile> transferFilesToMe() {
        try {
            System.out.println(this.getNodeName() + " - TRANSFER.FILES.TO.ME: " + this.nodeId);
            Object[] objArray = new Object[2];
            objArray[0] = MessageType.TRANSFER_YOUR_FILES_TO_ME;
            objArray[1] = this.nodeId;
            return (List<SharedFile>) Utils.sendMessage(this.getSuccessor().getAddress(), objArray);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList();
        }
    }


    /**
     * Search a file in the network by its title
     * @param searchTerm
     * @return search results
     */
    public List<SharedFile> searchFile(String searchTerm) {
        List<SharedFile> returnSharedFiles = new ArrayList();
        try {
            String searchString = searchTerm.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
            long fileId = Utils.hashFunction(searchString);
            // Locate file id in the network
            List<SharedFile> results = findFileById(new Pair(fileId, searchString));
            returnSharedFiles.addAll(results);
            return returnSharedFiles;
        } catch (Exception e) {
            e.printStackTrace();
            return returnSharedFiles;
        }
    }


    /**
     * Find a file in the network by looking for the node who holds this file
     * @param searchFile
     * @return list of files
     */
    public List<SharedFile> findFileById(Pair<Long, String> searchFile) {
        List<SharedFile> returnSharedFiles = new ArrayList();
        try {
            long fileId = searchFile.getKey();
            String searchString = searchFile.getValue();
            System.out.println(this.getNodeName() + " - FIND.FILE.BY.ID:" + fileId + ", term: " + searchString);

            // Find if I hold this file
            if (Utils.isIdBetweenUpperEq(fileId, this.getPredecessor().getNodeId(), this.getNodeId())) {
                System.out.println("I'm holding this file: " + fileId + ", " + searchString);
                for (SharedFile sharedFile : this.getSharedFileList()) {
                    if (sharedFile.getId() == fileId && sharedFile.getTitle().replaceAll("[^A-Za-z0-9]", "").toLowerCase().equals(searchString)) {
                        returnSharedFiles.add(sharedFile);
                    }
                }
            // Check finger table to find the node who holds this file
            } else {
                FingerTable fingerTable = this.getFingerTable();
                int iThFinger = fingerTable.findIthFingerOf(fileId); // Find the finger that stores information of the SharedFile ID
                if (iThFinger != 0) {
                    System.out.println(this.getNodeName() + " - FIND.FILE.BY.ID: Found file in the finger #" + iThFinger);
                    fingerTable.printFingerTable();

                    Node contact = fingerTable.getEntryNode(iThFinger);
                    // If I'm responsible for this file id
                    if (contact.getNodeId() == this.getNodeId()) {
                        System.out.println("I'm holding this file: " + fileId + ", " + searchString);
                        for (SharedFile sharedFile : this.getSharedFileList()) {
                            if (sharedFile.getId() == fileId && sharedFile.getTitle().replaceAll("[^A-Za-z0-9]", "").toLowerCase().equals(searchString)) {
                                returnSharedFiles.add(sharedFile);
                            }
                        }
                    } else {
                        // Ask i-th entry node to check file id
                        System.out.println(this.getNodeName() + " - FIND.FILE.SUCCESSOR: Contacting node #" + contact.getNodeId() + " (" + contact.getAddress().getAddress().getHostAddress() + ":" + contact.getAddress().getPort() + ")...");
                        Object[] objArray = new Object[2];
                        objArray[0] = MessageType.FIND_FILE;
                        objArray[1] = searchFile;
                        List<SharedFile> results = (List<SharedFile>) Utils.sendMessage(contact.getAddress(), objArray);
                        returnSharedFiles.addAll(results);
                    }
                }
            }
            return returnSharedFiles;
        } catch (Exception e) {
            e.printStackTrace();
            return returnSharedFiles;
        }
    }


    /**
     * Remove a shared file by telling the node who holds this file to remove it
     * @param file
     */
    public void removeSharedFile(Pair<Long, String> file) {
        try {
            long fileId = file.getKey();
            String fileTitleAddress = file.getValue();
            // Find file's successor
            // This file is assigned to me
            if ( fileId> this.getPredecessor().getNodeId() && fileId <= this.getNodeId()) {
                for (SharedFile b : this.getSharedFileList()) {
                    String titleAddress = b.getTitle() + b.getOwnerAddress().getAddress().getHostAddress() + ":" + b.getOwnerAddress().getPort();
                    if (b.getId() == fileId && titleAddress.equals(fileTitleAddress)) {
                        this.getSharedFileList().remove(b);
                    }
                }
            // Check finger table to find the node who holds this file
            }  else {
                // Contact file's successor to remove file
                FingerTable fingerTable = this.getFingerTable();
                int iThFinger = fingerTable.findIthFingerOf(fileId); // Find the finger that stores information of the SharedFile ID
                if (iThFinger != 0) {
                    System.out.println(this.getNodeName() + " - FIND.FILE.BY.ID: Found file in the finger #" + iThFinger);
                    fingerTable.printFingerTable();

                    Node contact = fingerTable.getEntryNode(iThFinger);
                    // If I'm responsible for this file id
                    if (contact.getNodeId() == this.getNodeId()) {
                        List<SharedFile> newSharedFileList = new ArrayList();
                        for (SharedFile b : this.getSharedFileList()) {
                            String titleAddress = b.getTitle() + b.getOwnerAddress().getAddress().getHostAddress() + ":" + b.getOwnerAddress().getPort();
                            // Remove this file from the list
                            if (b.getId() == fileId && titleAddress.equals(fileTitleAddress)) {
//                                this.getSharedFileList().remove(b);
                            } else {
                                newSharedFileList.add(b);
                            }
                        }
                        setSharedFileList(newSharedFileList);
                    } else {
                        // Ask i-th entry node to check file id
                        System.out.println(this.getNodeName() + " - FIND.FILE.SUCCESSOR: Contacting node #" + contact.getNodeId() + " (" + contact.getAddress().getAddress().getHostAddress() + ":" + contact.getAddress().getPort() + ")...");
                        Object[] objArray = new Object[2];
                        objArray[0] = MessageType.REMOVE_SHARED_FILE;
                        objArray[1] = file;
                        MessageType result = (MessageType) Utils.sendMessage(contact.getAddress(), objArray);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Stop all threads that have while loop
     */
    public void stopLoopThreads() {
        listener.closeListener();
        stabilize.closeStabilize();
        fixFingers.closeFixFingers();
    }


    /*****************************
     * Getter and setter methods
     */
    public static void setM(int m) {
        Node.m = m;
        chordRingSize = (long) Math.pow(2, m);
    }
    public static int getM() {
        return m;
    }

    public static long getChordRingSize() {
        chordRingSize = (long) Math.pow(2, m);
        return chordRingSize;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public Node getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(Node predecessor) {
        this.predecessor = predecessor;
    }

    public Node getSuccessor() {
        return successor;
    }

    public void setSuccessor(Node successor) {
        this.successor = successor;
    }

    public Node getSucSuccessor() {
        return sucSuccessor;
    }

    public void setSucSuccessor(Node sucSuccessor) {
        this.sucSuccessor = sucSuccessor;
    }

    public long getNodeId() {
        return nodeId;
    }

    public void setNodeId(long nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public FingerTable getFingerTable() {
        return fingerTable;
    }

    public void setFingerTable(FingerTable fingerTable) {
        this.fingerTable = fingerTable;
    }

    public Listener getListener() {
        return listener;
    }

    public List<SharedFile> getSharedFileList() {
        return sharedFileList;
    }

    public void setSharedFileList(List<SharedFile> sharedFileList) {
        this.sharedFileList = sharedFileList;
    }

    public List<SharedFile> getMySucSharedFileList() {
        return mySucSharedFileList;
    }

    public void setMySucSharedFileList(List<SharedFile> mySucSharedFileList) {
        this.mySucSharedFileList = mySucSharedFileList;
    }

    public List<Pair<Long, String>> getmySharedFiles() {
        return mySharedFiles;
    }

    public void setmySharedFiles(List<Pair<Long, String>> mySharedFiles) {
        this.mySharedFiles = mySharedFiles;
    }

    public List<Pair<Long, String>> getMySucSharedFiles() {
        return mySucSharedFiles;
    }

    public void setMySucSharedFiles(List<Pair<Long, String>> mySucSharedFiles) {
        this.mySucSharedFiles = mySucSharedFiles;
    }
}