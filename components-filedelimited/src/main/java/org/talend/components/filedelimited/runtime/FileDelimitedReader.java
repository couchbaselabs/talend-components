package org.talend.components.filedelimited.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.avro.generic.IndexedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.component.runtime.AbstractBoundedReader;
import org.talend.components.api.component.runtime.BoundedSource;
import org.talend.components.api.component.runtime.Result;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.filedelimited.FileDelimitedDefinition;
import org.talend.components.filedelimited.tFileInputDelimited.TFileInputDelimitedProperties;
import org.talend.daikon.avro.converter.IndexedRecordConverter;
import org.talend.fileprocess.FileInputDelimited;

import com.talend.csv.CSVReader;

public class FileDelimitedReader extends AbstractBoundedReader<IndexedRecord> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileDelimitedDefinition.class);

    private RuntimeContainer container;

    private transient IndexedRecord currentIndexRecord;

    private IndexedRecordConverter factory;

    private FileDelimitedRuntime fileDelimitedRuntime;

    private FileInputDelimited fid;

    private CSVReader csvReader;

    TFileInputDelimitedProperties properties;

    public FileDelimitedReader(RuntimeContainer container, BoundedSource source, TFileInputDelimitedProperties properties) {
        super(source);
        this.container = container;
        this.properties = properties;
        if (properties.csvOptions.getValue()) {
            factory = new CSVAdaptorFactory();
        } else {
            factory = new DelimitedAdaptorFactory();
        }
        factory.setSchema(properties.main.schema.getValue());
        fileDelimitedRuntime = new FileDelimitedRuntime(properties);

    }

    @Override
    public boolean start() throws IOException {
        fileDelimitedRuntime.init();
        LOGGER.debug("open: " + properties.fileName.getStringValue());
        boolean startAble = false;
        if (properties.csvOptions.getValue()) {
            csvReader = fileDelimitedRuntime.getCsvReader();
            startAble = fileDelimitedRuntime.limit > 0 && csvReader != null && csvReader.readNext();
        } else {
            fid = fileDelimitedRuntime.getFileDelimited();
            startAble = fid != null && fid.nextRecord();
        }
        return startAble;
    }

    @Override
    public boolean advance() throws IOException {
        boolean isContinue = csvReader.readNext();
        if (!isContinue) {
            if (properties.uncompress.getValue()) {
                if (properties.csvOptions.getValue()) {
                    csvReader = fileDelimitedRuntime.getCsvReader();
                    isContinue = csvReader != null && csvReader.readNext();
                } else {
                    fid = fileDelimitedRuntime.getFileDelimited();
                    isContinue = fid != null && fid.nextRecord();
                }
            }
        }
        return false;
    }

    @Override
    public IndexedRecord getCurrent() {
        String values = null;
        if (properties.csvOptions.getValue()) {
            currentIndexRecord = ((CSVAdaptorFactory) factory).convertToAvro(csvReader);
        } else {
            currentIndexRecord = ((DelimitedAdaptorFactory) factory).convertToAvro(fid);
        }
        return currentIndexRecord;
    }

    @Override
    public void close() throws IOException {
        if (!(fileDelimitedRuntime.fileNameOrStream instanceof InputStream)) {
            if (properties.csvOptions.getValue()) {
                if (csvReader != null) {
                    csvReader.close();
                }
            } else {
                if (fid != null) {
                    fid.close();
                }
            }
        }
        LOGGER.debug("close: " + properties.fileName.getStringValue());
    }

    @Override
    public Map<String, Object> getReturnValues() {
        return new Result().toMap();
    }

}
