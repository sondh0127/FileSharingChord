package App.RunnableThread;

import App.Components.MessageType;
import App.Components.SharedFile;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;

import java.io.*;
import java.net.Socket;

public class Download implements Runnable{

    protected Socket socket = null;
    protected SharedFile currentSharedFile = null;
    protected File destinationDir = null;
    protected ProgressBar progressBar = null;
    protected CheckBox checkBox = null;

    public Download(Socket socket, SharedFile sharedFile, File dir, ProgressBar bar, CheckBox checkBox) {
        this.socket = socket;
        this.currentSharedFile = sharedFile;
        this.destinationDir = dir;
        this.progressBar = bar;
        this.checkBox = checkBox;
    }

    public void run() {
        try {
            System.out.println("Download running");

            // Send SharedFile's file location to the owner
            Object[] msgArray = new Object[2];
            msgArray[0] = MessageType.DOWNLOAD_BOOK;
            msgArray[1] = currentSharedFile.getLocation();
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(msgArray);
            out.flush();

            // Setup file path
            String filePath = "";
            if (destinationDir.toString().lastIndexOf("/") > 0) {
                filePath = destinationDir + "/";
            } else {
                filePath = destinationDir + "\\";
            }
            filePath += currentSharedFile.getTitle() + currentSharedFile.getLocation().substring(currentSharedFile.getLocation().lastIndexOf("."));
            System.out.println("downloading file's destination: " + filePath);

            // receive file size
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            int fileSize = (Integer) in.readObject();
            System.out.println("File size " + fileSize);
            byte [] byteArray  = new byte [ fileSize + 1];

            //reading file from socket
            System.out.println("Downloading file");
            InputStream inputStream = socket.getInputStream();
            FileOutputStream fileOutputStream = new FileOutputStream(filePath);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);

            int bytesRead = 0;
            int current = 0;
            bytesRead = inputStream.read(byteArray,0,byteArray.length);					//copying file from socket to byteArray
            current = bytesRead;
            do {
                bytesRead =inputStream.read(byteArray, current, (byteArray.length-current));
                if(bytesRead >= 0) current += bytesRead;
                progressBar.setProgress((current/ (float)fileSize));
//                progressIndicator.setProgress((current/ (float)fileSize));
//                System.out.println("progress: " + (current/ (float)fileSize));
            } while(bytesRead > -1);
            bufferedOutputStream.write(byteArray, 0 , current);							//writing byteArray to file
            bufferedOutputStream.flush();												//flushing buffers

            checkBox.setVisible(true);
            System.out.println("File " + filePath + " downloaded ( size: " + current + " bytes read)");

            out.close();
            in.close();
            inputStream.close();
            fileOutputStream.close();
            bufferedOutputStream.close();
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}