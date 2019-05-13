package test;

import App.Components.SharedFile;
import App.Components.Node;
import App.Controller;

public class TestSharingFiles {

    public static void main(String[] args) throws Exception{

        Node.setM(3);
        System.out.println("Chord ring size=" + Node.getChordRingSize());

        // First Node
        TestBase.createSocket(0, "N0");
        Node firstNode = Controller.getMyNode();
        firstNode.createNewNetwork(); // Create new network

        // Second Node
        System.out.println("\n\n*****************************************************");
        System.out.println("*****************************************************");
        System.out.println("Second Node\n\n");
        TestBase.createSocket(3, "N3");
        Node secondNode = Controller.getMyNode();
        secondNode.joinNetwork(firstNode.getAddress()); // Join first node's network

        // First Node share a file
        System.out.println("\n\n-----------------------------------------------------");
        firstNode.shareAFile("firstFile", "firstAuthor", "", "/User/SharedFile/firstfile.pdf");
        System.out.println("-----------------------------------------------------");
        firstNode.shareAFile("secondFile", "firstAuthor", "", "/User/SharedFile/secondfile.pdf");
        System.out.println("-----------------------------------------------------");
        firstNode.shareAFile("thirdFile", "firstAuthor", "", "/User/SharedFile/thirdfile.pdf");
        System.out.println("-----------------------------------------------------");

        firstNode.getFingerTable().printFingerTable();
        System.out.println("N0's SUC is " + firstNode.getSuccessor().getNodeName() + ", PRE is " + firstNode.getPredecessor().getNodeName());
        for (SharedFile b : firstNode.getSharedFileList()) {
            System.out.println("N0 - SharedFile " + b.getId() + ": " + b.getTitle() + " - " + b.getAuthor() + " - " + b.getLocation());
        }

        secondNode.getFingerTable().printFingerTable();
        System.out.println("N3's SUC is " + secondNode.getSuccessor().getNodeName() + ", PRE is " + secondNode.getPredecessor().getNodeName());
        for (SharedFile b : secondNode.getSharedFileList()) {
            System.out.println("N3 - SharedFile " + b.getId() + ": " + b.getTitle() + " - " + b.getAuthor() + " - " + b.getLocation());
        }
    }
}
