package org.app.controller;

import com.opencsv.CSVWriter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.app.model.ExportDeclaration;
import org.app.service.PdfFolderService;

import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;

public class MainController {
    @FXML private Button browseButton;
    @FXML private TextArea logArea;
    @FXML private ProgressIndicator spinner;


    @FXML private void onBrowse() {
        File folder = new DirectoryChooser()
                .showDialog((Stage) browseButton.getScene().getWindow());
        if (folder == null) {
            log("Folder selection cancelled.");
            return;
        }
        log("Processing folder: " + folder);

        // 1) Create a Task that does the work off the FX thread
        Task<List<ExportDeclaration>> task = new Task<>() {
            @Override
            protected List<ExportDeclaration> call() throws Exception {
                PdfFolderService svc = new PdfFolderService(line ->
                        Platform.runLater(() -> log(line))
                );
                return svc.processFolder(folder);
            }
        };

        // 2) Wire its messageProperty to your logArea
        task.messageProperty().addListener((obs, old, msg) -> {
            if (msg != null && !msg.isBlank()) {
                log(msg);
            }
        });

        // Bind spinner visibility to the fact that the task is running
        spinner.visibleProperty().bind(task.runningProperty());

        // Disable your browse button while the task is running
        browseButton.disableProperty().bind(task.runningProperty());

        // 3) When it succeeds, write out the CSV on the FX thread
        task.setOnSucceeded(e -> {
            try {
                writeCsv(new File(folder, "output.csv"), task.getValue());
            } catch (Exception ex) {
                log("Error writing CSV: " + ex.getMessage());
            }
        });
        task.setOnFailed(e -> {
            log("Fatal: " + task.getException().getMessage());
        });

        // 4) Kick it off
        new Thread(task, "pdf-extractor").start();
    }

    private void writeCsv(File outFile, List<ExportDeclaration> list) throws Exception {
        // for rounding the 2.5% commission to 2 decimals
        DecimalFormat df = new DecimalFormat("#.##");

        try (CSVWriter writer = new CSVWriter(new FileWriter(outFile))) {
            // 1) Header
            writer.writeNext(new String[]{
                    "nr.crt",
                    "CIF/CNP",
                    "client",
                    "deviz",
                    "produs",
                    "Serie produs",
                    "Cant",
                    "UM",
                    "Pret FTVA",
                    "cota TVA",
                    "nota produs",
                    "scutit TVA (0/1)",
                    "motiv scutire TVA"
            });

            int counter = 1;
            for (ExportDeclaration dto : list) {
                // --- 1) primary row ---
                String[] primary = {
                        String.valueOf(counter),        // nr.crt
                        "RO33706828",                   // CIF/CNP
                        dto.getNumeExportatorComplet(),                    //client
                        "EUR",                          // deviz
                        "PREST. VAMALE IMP/EXP",        // produs
                        "",                             // Serie produs
                        "1",                            // Cant
                        "BUC",                          // UM
                        "50",                           // Pret FTVA logic
                        "21",                           // cota TVA
                        "DVE:" + dto.getMrn() + "/" + dto.getDataDeclaratie().replace("-", ".")
                                + "/" + dto.getNrContainer() + "-" + dto.getNumeExportator(),          // nota produs
                        "",                             // scutit TVA
                        ""                              // motiv scutire TVA
                };
                writer.writeNext(primary);
                counter++;
            }
        }

        log("CSV written to: " + outFile.getAbsolutePath());
    }


    private void log(String message) {
        logArea.appendText(message + "\n");
    }
}
