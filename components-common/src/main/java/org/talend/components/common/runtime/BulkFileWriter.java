package org.talend.components.common.runtime;

import com.csvreader.CsvWriter;
import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.talend.components.api.component.runtime.Sink;
import org.talend.components.api.component.runtime.WriteOperation;
import org.talend.components.api.component.runtime.Writer;
import org.talend.components.api.component.runtime.Result;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.common.BulkFileProperties;
import org.talend.daikon.avro.IndexedRecordAdapterFactory;
import org.talend.daikon.avro.util.AvroUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate bulk file
 */
public class BulkFileWriter implements Writer<Result> {

    protected RuntimeContainer container;

    private WriteOperation<Result> writeOperation;

    private Result result;

    private Sink sink;

    protected BulkFileProperties bulkProperties;

    private String uId;

    private CsvWriter csvWriter;

    private char separator = ',';

    private String charset = "UTF-8";

    private boolean isAppend;

    private transient IndexedRecordAdapterFactory<IndexedRecord, IndexedRecord> factory;

    public BulkFileWriter(WriteOperation<Result> writeOperation, BulkFileProperties bulkProperties,
                          RuntimeContainer container) {
        this.writeOperation = writeOperation;
        this.container = container;
        this.sink = writeOperation.getSink();
        this.bulkProperties = bulkProperties;
        this.isAppend = bulkProperties.append.getValue();
    }

    @Override
    public void open(String uId) throws IOException {
        this.uId = uId;
        this.result = new Result(uId);
        File file = new File(bulkProperties.bulkFilePath.getStringValue());
        file.getParentFile().mkdirs();
        csvWriter = new CsvWriter(new OutputStreamWriter(new java.io.FileOutputStream(file, isAppend), charset), separator);

        fileIsEmpty = (file.length() == 0);
    }

    private boolean headerIsReady = false;

    private boolean fileIsEmpty = false;

    @Override
    public void write(Object datum) throws IOException {
        if (null == datum) {
            return;
        }

        if (!headerIsReady && (!isAppend || fileIsEmpty)) {
            Schema schema = new Schema.Parser().parse(bulkProperties.schema.schema.getStringValue());

            if (AvroUtils.isIncludeAllFields(schema) && (datum instanceof org.apache.avro.generic.IndexedRecord)) {
                org.apache.avro.generic.IndexedRecord record = (org.apache.avro.generic.IndexedRecord) datum;
                schema = record.getSchema();
            }

            csvWriter.writeRecord(getHeaders(schema));
            headerIsReady = true;
        }

        List<String> values = getValues(datum);
        csvWriter.writeRecord(values.toArray(new String[values.size()]));
        result.totalCount++;
    }

    public void flush() throws IOException {
        csvWriter.flush();
    }

    @Override
    public Result close() throws IOException {
        flush();
        csvWriter.close();
        return result;
    }

    @Override
    public WriteOperation<Result> getWriteOperation() {
        return writeOperation;
    }

    public String[] getHeaders(Schema schema) {
        List<String> headers = new ArrayList<String>();
        for (Schema.Field f : schema.getFields()) {
            headers.add(f.name());
        }
        return headers.toArray(new String[headers.size()]);
    }

    public List<String> getValues(Object datum) {
        IndexedRecord input = getFactory(datum).convertToAvro((IndexedRecord) datum);
        List<String> values = new ArrayList<String>();
        for (Schema.Field f : input.getSchema().getFields()) {
            if (input.get(f.pos()) != null) {
                values.add(String.valueOf(input.get(f.pos())));
            } else {
                values.add("");
            }
        }
        return values;
    }

    public IndexedRecordAdapterFactory<IndexedRecord, IndexedRecord> getFactory(Object datum) {
        if (null == factory) {
            factory = new GenericAdapterFactory();
            factory.setSchema(((IndexedRecord) datum).getSchema());
        }
        return factory;
    }
}
