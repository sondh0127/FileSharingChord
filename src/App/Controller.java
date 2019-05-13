package App;

import App.Components.SharedFile;
import App.Components.MessageType;
import App.Components.Node;

import App.Components.Utils;
import App.RunnableThread.Download;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Pair;

import java.io.*;
import java.net.*;
import java.util.List;

public class Controller {

    private static Node myNode = null;
    public static Node getMyNode() {
        return myNode;
    }
    public static void setMyNode(Node n) {myNode = n; }

    public static void stopLoopThreads() {
        if (myNode != null) {
            myNode.stopLoopThreads();
            db.unshareAllFiles(myNode.getAddress());
        }
    }

    /**
     * Check if given address is available
     * @param address
     * @return true if given address is available, false otherwise.
     */
    public static boolean available(InetSocketAddress address) {
//        try (Socket ignored = new Socket(address.getAddress(), address.getPort())) {
        Socket ignored = new Socket();
        try {
            System.out.println("Connecting to " + address.getAddress().getHostAddress() + ":" + address.getPort());
            ignored.connect(address, 1000);
            // Send message to server to check port availability
            Object[] objArray = new Object[1];
            objArray[0] = MessageType.CHECKING_IF_PORT_IS_AVAILABLE;
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(ignored.getOutputStream());
            objectOutputStream.writeObject(objArray);
            objectOutputStream.flush();

            // Receive response message
            ObjectInputStream objectInputStream = new ObjectInputStream(ignored.getInputStream());
            objectInputStream.readObject();

            objectInputStream.close();
            objectOutputStream.close();
            ignored.close();

            System.out.println("port #" + address.getPort() + " not available");
            return false;
        } catch (SocketTimeoutException e ) {
            System.out.println("port #" + address.getPort() + " available");
            return true;
        } catch (ConnectException e ) {
            System.out.println("port #" + address.getPort() + " available");
            return true;
        } catch (Exception e) {
//            e.printStackTrace();
            System.out.println("port #" + address.getPort() + " available");
            return true;
        }
    }

    private static InetAddress getLocalHost() {
        try {
//            return InetAddress.getByName("127.0.0.1");
            return InetAddress.getLocalHost();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Find available port, create server socket, and initialize Chord node
     */
    public static void createSocketWhenAppStarts() {
        try {
            int initialPort = 1111;
            InetSocketAddress myAddress = new InetSocketAddress(getLocalHost(), initialPort);
            while (!available(myAddress)) {
                initialPort += 1;
//                System.out.println("Checking port #" + initialPort);
                myAddress = new InetSocketAddress(getLocalHost(), initialPort);
            }
            Controller.myNode = new Node(myAddress);
            System.out.println("Socket created on " + myAddress.getAddress().getHostAddress() + ":" + myAddress.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private Label addressLabel;

    @FXML
    private static SQLiteDB db;
    /**
     * Initialize stuff when main starts
     */
    public void initialize() {
        try {
            InetSocketAddress address = Controller.myNode.getAddress();
            addressLabel.setText("My address: " + address.getAddress().getHostAddress() + ":" + address.getPort() + " - Node Id: " + Controller.myNode.getNodeId());

            // Load files from local database
            db = new SQLiteDB();
            for (SharedFile sharedFile : db.getAllMyFiles(address)) {
                System.out.println("File from the db: " + sharedFile.getTitle() + ", " + sharedFile.getLocation());
                File file = new File(sharedFile.getLocation());
                if (file.exists()) {
                    // Share each sharedFile with the network
                    SharedFile newSharedFile = getMyNode().shareAFile(sharedFile.getTitle(), sharedFile.getAuthor(), sharedFile.getIsbn(), sharedFile.getLocation());
                    if (newSharedFile != null) {
                        sharedFile.setIsShared(true);
                        db.updateFileShareStatus(newSharedFile);

                        // Add new sharedFile to my shared sharedFile list
                        Pair<Long, String> pair = new Pair(newSharedFile.getId(), newSharedFile.getTitle());
                        Controller.getMyNode().getmySharedFiles().add(pair);

                        System.out.println("Successfully re-shared file " + newSharedFile.getId() + " " + newSharedFile.getTitle() + " " + newSharedFile.getLocation());
                    } else {
                        sharedFile.setIsShared(false);
                        db.updateFileShareStatus(sharedFile);
                    }
                } else {
                    sharedFile.setIsShared(false);
                    db.updateFileShareStatus(sharedFile);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printInfo(ActionEvent event) {
        try {
            System.out.println("\n\nMy successor: " + Controller.getMyNode().getSuccessor().getNodeName());
            System.out.println("My predecessor: " + Controller.getMyNode().getPredecessor().getNodeName());
            Controller.getMyNode().getFingerTable().printFingerTable();
            System.out.println("My files: " + Controller.getMyNode().getmySharedFiles().size());
            for (Pair p : Controller.getMyNode().getmySharedFiles()) {
                System.out.println("My file: " + p.getKey() + ", " + p.getValue());
            }

            System.out.println("\nOthers' shared files: " + Controller.getMyNode().getSharedFileList().size());
            for (SharedFile b : Controller.getMyNode().getSharedFileList()) {
                System.out.println("File shared by others: " + b.getId() + ", " + b.getTitle() + ", " + b.getLocation() + ". " + b.getOwnerAddress().getAddress().getHostAddress() + ":" + b.getOwnerAddress().getPort());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Search a file tab
     **/

    @FXML
    private TextField searchTextField;

    @FXML
    private Text searchAlertText;

    @FXML
    private ListView<SharedFile> fileListView;

    public void searchAFileTabSelected(Event event) {
        searchAlertText.setText("");
        ObservableList<SharedFile> list = FXCollections.observableArrayList();
        fileListView.setItems(list);
    }

    class FileCell extends ListCell<SharedFile> {
        HBox hbox = new HBox();
        Label label = new Label("(empty)");
        Pane pane = new Pane();
        Button downloadButton = new Button("Download");
        BorderPane borderPane = new BorderPane();
        CheckBox checkBox = new CheckBox();
        BorderPane progressBorderPane = new BorderPane();
        ProgressBar progressBar = new ProgressBar(0);
        SharedFile currentSharedFile;

        public FileCell() {
            super();
            downloadButton.setDisable(false);
            borderPane.setTop(downloadButton);
            borderPane.setCenter(new Label(""));
            borderPane.setBottom(progressBorderPane);

            progressBorderPane.setLeft(progressBar);
            progressBorderPane.setCenter(new Label(" "));
            progressBorderPane.setRight(checkBox);

            hbox.getChildren().addAll(label, pane, borderPane);
            HBox.setHgrow(pane, Priority.ALWAYS);

            // Download Button is pressed
            downloadButton.setOnAction(e -> {
                // Choose directory
                DirectoryChooser dc = new DirectoryChooser();
                File dir = dc.showDialog(null);
                if (dir != null) {
                    try {
                        System.out.println("directory selected: " + dir);
                        progressBar.setProgress(0);

                        // Ask file owner if file is still available
                        Object[] objArray = new Object[2];
                        objArray[0] = MessageType.IS_FILE_AVAILABLE;
                        objArray[1] = currentSharedFile.getLocation();
                        MessageType response = (MessageType) Utils.sendMessage(currentSharedFile.getOwnerAddress(), objArray);
                        if (response == MessageType.FILE_IS_AVAILABLE) {
                            //creating connection to owner's socket
                            Socket socket = new Socket(currentSharedFile.getOwnerAddress().getAddress(), currentSharedFile.getOwnerAddress().getPort());
                            System.out.println("Connecting to file owner: " + currentSharedFile.getOwnerAddress().getAddress().getHostAddress() + ":" + currentSharedFile.getOwnerAddress().getPort());

                            Thread t = new Thread(new Download(socket, currentSharedFile, dir, progressBar, checkBox));
                            t.start();
                            System.out.println("Downloading " + currentSharedFile.getTitle() + " from loc: " + currentSharedFile.getLocation());
                        } else {
                            searchAlertText.setText("SharedFile is no longer available to download from this user!");

                            // Make the download button disabled
                            downloadButton.setDisable(true);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    searchAlertText.setText("Please choose a directory to download");
                }
            });
        }

        @Override
        protected void updateItem(SharedFile newSharedFile, boolean empty) {
            super.updateItem(newSharedFile, empty);
            setText(null);  // No text in label of super class
            if (empty) {
                currentSharedFile = null;
                setGraphic(null);
            } else {
                currentSharedFile = newSharedFile;
                if (currentSharedFile.getIsbn().isEmpty()) {
                    label.setText(currentSharedFile.getTitle() + " by " + currentSharedFile.getAuthor() + "\n Owner: " + currentSharedFile.getOwnerAddress().getAddress().getHostAddress() + ":" + currentSharedFile.getOwnerAddress().getPort());
                } else {
                    label.setText(currentSharedFile.getTitle() + " by " + currentSharedFile.getAuthor() + " (isbn:" + currentSharedFile.getIsbn() + ")" + "\n Owner: " + currentSharedFile.getOwnerAddress().getAddress().getHostAddress() + ":" + currentSharedFile.getOwnerAddress().getPort());
                }
                checkBox.setSelected(true);
                checkBox.setDisable(true);
                checkBox.setVisible(false);
                checkBox.setText("Done");
                setGraphic(hbox);
            }
        }
    }

    public void searchFile(ActionEvent event) {
        try {
            searchAlertText.setText("");
            String searchTerm = searchTextField.getText();
            if (searchTerm.isEmpty()) {
                searchAlertText.setText("No SharedFile found!");
            } else {
                List<SharedFile> searchSharedFileResult = myNode.searchFile(searchTerm);
                if (searchSharedFileResult.isEmpty()) {
                    searchAlertText.setText("No SharedFile found!");
                } else {
                    ObservableList<SharedFile> list = FXCollections.observableArrayList();
                    list.addAll(searchSharedFileResult);
                    fileListView.setItems(list);
                    fileListView.setCellFactory(param -> new FileCell());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Share a file tab
     **/

    @FXML
    private Text chooseFileText;

    @FXML
    private Text alertText;

    @FXML
    private TextField titleTextField;

    @FXML
    private TextField authorTextField;

    @FXML
    private TextField isbnTextField;

    private File selectedFile;

    public void shareAFileTabSelected(Event event) {
        alertText.setText("Enter SharedFile Information");
        titleTextField.setText("");
        authorTextField.setText("");
        isbnTextField.setText("");
        chooseFileText.setText("");
    }

    public void chooseFileButtonSelected(ActionEvent event) {
        FileChooser fc = new FileChooser();
        selectedFile = fc.showOpenDialog(null);

        if (selectedFile != null) {
            chooseFileText.setText("File selected: " + selectedFile);
        } else {
            alertText.setText("No file selected");
        }
    }

    public void shareNewFile(ActionEvent event) {
//        System.out.println("Save button pressed");
        alertText.setText("");
        try {
            if (titleTextField.getText().isEmpty()) {
                alertText.setText("Title can't be empty");
            } else if (authorTextField.getText().isEmpty()) {
                alertText.setText("Author can't be empty");
            } else if (selectedFile == null) {
                alertText.setText("Please choose a file");
            } else if (!selectedFile.exists()) {
                alertText.setText("Selected file doesn't exist");
            } else {

                // Check if file is already shared
                boolean status = db.checkIfFileExists(Controller.getMyNode().getAddress(), selectedFile.toString());

                if (status) {
                    alertText.setText("This SharedFile is already shared: " + selectedFile.toString());
                } else {
                    // Share a new file with the network
                    SharedFile newSharedFile = Controller.getMyNode().shareAFile(titleTextField.getText(), authorTextField.getText(), isbnTextField.getText(), selectedFile.toString());
                    if (newSharedFile != null) {

                        // Add file to the database
                        db.addNewFile(newSharedFile);

                        // Add new file to my shared file list
                        Pair<Long, String> pair = new Pair(newSharedFile.getId(), newSharedFile.getTitle());
                        getMyNode().getmySharedFiles().add(pair);

                        alertText.setText("New SharedFile '" + titleTextField.getText() + "' successfully shared");
                        titleTextField.setText("");
                        authorTextField.setText("");
                        isbnTextField.setText("");
                        chooseFileText.setText("");
                    } else {
                        alertText.setText("Can't share file! Please try again!");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    /**
     * My shared files tab
     **/

    @FXML
    private ListView<SharedFile> mySharedFilesListView;

    class SharedFileCell extends ListCell<SharedFile> {
        HBox hbox = new HBox();
        Label titleLabel = new Label("");
        Label authorLabel = new Label("");
        Label isbnLabel = new Label("");
        Label locationLabel = new Label("");
        Label fileIDLabel = new Label("");
        Pane pane = new Pane();
        BorderPane titleAuthorBorderPane = new BorderPane();
        BorderPane borderPane = new BorderPane();
        BorderPane statusBorderPane = new BorderPane();
        Button updateLocButton = new Button("Update File's Location");
        CheckBox shareStatus = new CheckBox();

        SharedFile currentSharedFile;

        public SharedFileCell() {
            super();
            titleAuthorBorderPane.setTop(titleLabel);
            titleAuthorBorderPane.setLeft(authorLabel);
            titleAuthorBorderPane.setBottom(isbnLabel);

            borderPane.setTop(fileIDLabel);
            borderPane.setLeft(locationLabel);
            borderPane.setBottom(statusBorderPane);

            statusBorderPane.setTop(shareStatus);
            statusBorderPane.setLeft(updateLocButton);

            hbox.getChildren().addAll(titleAuthorBorderPane, pane, borderPane);
            HBox.setHgrow(pane, Priority.ALWAYS);

            // Update Location Button is pressed
            updateLocButton.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                selectedFile = fc.showOpenDialog(null);

                if (selectedFile != null) {
                    String newLoc = selectedFile.toString();

                    // share file with the network
                    SharedFile newSharedFile = getMyNode().shareAFile(currentSharedFile.getTitle(), currentSharedFile.getAuthor(), currentSharedFile.getIsbn(), newLoc);

                    if (newSharedFile != null) {
                        //update new location with the database
                        currentSharedFile.setIsShared(true);
                        boolean status = db.updateFileLocation(currentSharedFile, newSharedFile);

                        if (status == true) {

                            System.out.println("Update Location - Successfully shared the file " + newSharedFile.getId());
                            // Add new file to my shared file list
                            Pair<Long, String> pair = new Pair(newSharedFile.getId(), newSharedFile.getTitle());
                            getMyNode().getmySharedFiles().add(pair);

                            refreshmySharedFilesTab();
                        }
                    }
                }
            });
        }

        @Override
        protected void updateItem(SharedFile newSharedFile, boolean empty) {
            super.updateItem(newSharedFile, empty);
            setText(null);  // No text in label of super class
            if (empty) {
                currentSharedFile = null;
                setGraphic(null);
            } else {
                currentSharedFile = newSharedFile;
                titleLabel.setText("SharedFile title: " + currentSharedFile.getTitle());
                authorLabel.setText("Author: " + currentSharedFile.getAuthor());
                isbnLabel.setText("ISBN: " + currentSharedFile.getIsbn());
                locationLabel.setText("Location:" + currentSharedFile.getLocation());
                fileIDLabel.setText("SharedFile ID= : " + currentSharedFile.getId() + ", Owner: " + currentSharedFile.getOwnerAddress().getAddress().getHostAddress() + ":"  + currentSharedFile.getOwnerAddress().getPort());
                setGraphic(hbox);

                // check if file exists
                if (!currentSharedFile.getIsShared()) { // if file doesn't exist, show option to update location
                    updateLocButton.setVisible(true);
                    shareStatus.setSelected(false);
                    shareStatus.setDisable(false);
                    shareStatus.setText("File doesn't exist! Please Update file!");
                    shareStatus.setTextFill(Color.web("red"));
                } else {
                    updateLocButton.setVisible(false);
                    shareStatus.setSelected(true);
                    shareStatus.setDisable(true);
                    shareStatus.setText("Successfully shared!");
                    shareStatus.setTextFill(Color.web("blue"));


                }
            }
        }
    }

    public void mySharedFilesTabSelected(Event event) {
        refreshmySharedFilesTab();
    }

    private void refreshmySharedFilesTab() {
        try {
            System.out.println("Refreshing My Shared Files tab");
            // Load files in the local database
            ObservableList<SharedFile> list = FXCollections.observableArrayList();

            // load files from the local database
            List<SharedFile> sharedFileList = db.getAllMyFiles(getMyNode().getAddress());
            System.out.println(sharedFileList.size());

            list.addAll(sharedFileList);
            mySharedFilesListView.setItems(list);
            mySharedFilesListView.setCellFactory(param -> new SharedFileCell());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

