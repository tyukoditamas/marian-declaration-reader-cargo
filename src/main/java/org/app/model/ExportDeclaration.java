package org.app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportDeclaration {
    private String numeExportator;
    private String mrn;
    private String dataDeclaratie;
    private String nrContainer;
}
