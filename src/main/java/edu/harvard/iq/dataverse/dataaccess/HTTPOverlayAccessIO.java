package edu.harvard.iq.dataverse.dataaccess;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DvObject;
import edu.harvard.iq.dataverse.datavariable.DataVariable;
import edu.harvard.iq.dataverse.util.UrlSignerUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Logger;

import org.apache.http.Header;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HTTP;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;

/**
 * @author qqmyers
 * @param <T> what it stores
 */
/*
 * HTTP Overlay Driver
 * 
 * StorageIdentifier format:
 * <httpDriverId>://<baseStorageIdentifier>//<baseUrlPath>
 */
public class HTTPOverlayAccessIO<T extends DvObject> extends StorageIO<T> {

    private static final Logger logger = Logger.getLogger("edu.harvard.iq.dataverse.dataaccess.HttpOverlayAccessIO");

    private StorageIO<DvObject> baseStore = null;
    private String urlPath = null;
    private String baseUrl = null;

    private static HttpClientContext localContext = HttpClientContext.create();
    private PoolingHttpClientConnectionManager cm = null;
    CloseableHttpClient httpclient = null;
    private int timeout = 1200;
    private RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout * 1000)
            .setConnectionRequestTimeout(timeout * 1000).setSocketTimeout(timeout * 1000)
            .setCookieSpec(CookieSpecs.STANDARD).setExpectContinueEnabled(true).build();
    private static boolean trustCerts = false;
    private int httpConcurrency = 4;

    public HTTPOverlayAccessIO(T dvObject, DataAccessRequest req, String driverId) throws IOException {
        super(dvObject, req, driverId);
        this.setIsLocalFile(false);
        configureStores(req, driverId, null);
        logger.fine("Parsing storageidentifier: " + dvObject.getStorageIdentifier());
        // TODO: validate the storage location supplied
        urlPath = dvObject.getStorageIdentifier().substring(dvObject.getStorageIdentifier().lastIndexOf("//") + 2);
        logger.fine("Base URL: " + urlPath);
    }

    public HTTPOverlayAccessIO(String storageLocation, String driverId) throws IOException {
        super(null, null, driverId);
        this.setIsLocalFile(false);
        configureStores(null, driverId, storageLocation);

        // TODO: validate the storage location supplied
        urlPath = storageLocation.substring(storageLocation.lastIndexOf("//") + 2);
        logger.fine("Base URL: " + urlPath);
    }

    @Override
    public void open(DataAccessOption... options) throws IOException {

        baseStore.open(options);

        DataAccessRequest req = this.getRequest();

        if (isWriteAccessRequested(options)) {
            isWriteAccess = true;
            isReadAccess = false;
        } else {
            isWriteAccess = false;
            isReadAccess = true;
        }

        if (dvObject instanceof DataFile) {
            String storageIdentifier = dvObject.getStorageIdentifier();

            DataFile dataFile = this.getDataFile();

            if (req != null && req.getParameter("noVarHeader") != null) {
                baseStore.setNoVarHeader(true);
            }

            if (storageIdentifier == null || "".equals(storageIdentifier)) {
                throw new FileNotFoundException("Data Access: No local storage identifier defined for this datafile.");
            }

            // Fix new DataFiles: DataFiles that have not yet been saved may use this method
            // when they don't have their storageidentifier in the final form
            // So we fix it up here. ToDo: refactor so that storageidentifier is generated
            // by the appropriate StorageIO class and is final from the start.
            logger.fine("StorageIdentifier is: " + storageIdentifier);

            if (isReadAccess) {
                if (dataFile.getFilesize() >= 0) {
                    this.setSize(dataFile.getFilesize());
                } else {
                    logger.fine("Setting size");
                    this.setSize(getSizeFromHttpHeader());
                }
                if (dataFile.getContentType() != null && dataFile.getContentType().equals("text/tab-separated-values")
                        && dataFile.isTabularData() && dataFile.getDataTable() != null && (!this.noVarHeader())) {

                    List<DataVariable> datavariables = dataFile.getDataTable().getDataVariables();
                    String varHeaderLine = generateVariableHeader(datavariables);
                    this.setVarHeader(varHeaderLine);
                }

            }

            this.setMimeType(dataFile.getContentType());

            try {
                this.setFileName(dataFile.getFileMetadata().getLabel());
            } catch (Exception ex) {
                this.setFileName("unknown");
            }
        } else if (dvObject instanceof Dataset) {
            throw new IOException(
                    "Data Access: HTTPOverlay Storage driver does not support dvObject type Dataverse yet");
        } else if (dvObject instanceof Dataverse) {
            throw new IOException(
                    "Data Access: HTTPOverlay Storage driver does not support dvObject type Dataverse yet");
        } else {
            this.setSize(getSizeFromHttpHeader());
        }
    }

    private long getSizeFromHttpHeader() {
        long size = -1;
        HttpHead head = new HttpHead(baseUrl + "/" + urlPath);
        try {
            CloseableHttpResponse response = getSharedHttpClient().execute(head, localContext);

            try {
                int code = response.getStatusLine().getStatusCode();
                logger.fine("Response for HEAD: " + code);
                switch (code) {
                case 200:
                    Header[] headers = response.getHeaders(HTTP.CONTENT_LEN);
                    logger.fine("Num headers: " + headers.length);
                    String sizeString = response.getHeaders(HTTP.CONTENT_LEN)[0].getValue();
                    logger.fine("Content-Length: " + sizeString);
                    size = Long.parseLong(response.getHeaders(HTTP.CONTENT_LEN)[0].getValue());
                    logger.fine("Found file size: " + size);
                    break;
                default:
                    logger.warning("Response from " + head.getURI().toString() + " was " + code);
                }
            } finally {
                EntityUtils.consume(response.getEntity());
            }
        } catch (Exception e) {
            logger.warning(e.getMessage());
        }
        return size;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (super.getInputStream() == null) {
            try {
                HttpGet get = new HttpGet(baseUrl + "/" + urlPath);
                CloseableHttpResponse response = getSharedHttpClient().execute(get, localContext);

                int code = response.getStatusLine().getStatusCode();
                switch (code) {
                case 200:
                    setInputStream(response.getEntity().getContent());
                    break;
                default:
                    logger.warning("Response from " + get.getURI().toString() + " was " + code);
                    throw new IOException("Cannot retrieve: " + baseUrl + "/" + urlPath + " code: " + code);
                }
            } catch (Exception e) {
                logger.warning(e.getMessage());
                e.printStackTrace();
                throw new IOException("Error retrieving: " + baseUrl + "/" + urlPath + " " + e.getMessage());

            }
            setChannel(Channels.newChannel(super.getInputStream()));
        }
        return super.getInputStream();
    }

    @Override
    public Channel getChannel() throws IOException {
        if (super.getChannel() == null) {
            getInputStream();
        }
        return channel;
    }

    @Override
    public ReadableByteChannel getReadChannel() throws IOException {
        // Make sure StorageIO.channel variable exists
        getChannel();
        return super.getReadChannel();
    }

    @Override
    public void delete() throws IOException {
        // Delete is best-effort - we tell the remote server and it may or may not
        // implement this call
        if (!isDirectAccess()) {
            throw new IOException("Direct Access IO must be used to permanently delete stored file objects");
        }
        try {
            HttpDelete del = new HttpDelete(baseUrl + "/" + urlPath);
            CloseableHttpResponse response = getSharedHttpClient().execute(del, localContext);
            try {
                int code = response.getStatusLine().getStatusCode();
                switch (code) {
                case 200:
                    logger.fine("Sent DELETE for " + baseUrl + "/" + urlPath);
                default:
                    logger.fine("Response from DELETE on " + del.getURI().toString() + " was " + code);
                }
            } finally {
                EntityUtils.consume(response.getEntity());
            }
        } catch (Exception e) {
            logger.warning(e.getMessage());
            throw new IOException("Error deleting: " + baseUrl + "/" + urlPath);

        }

        // Delete all the cached aux files as well:
        deleteAllAuxObjects();

    }

    @Override
    public Channel openAuxChannel(String auxItemTag, DataAccessOption... options) throws IOException {
        return baseStore.openAuxChannel(auxItemTag, options);
    }

    @Override
    public boolean isAuxObjectCached(String auxItemTag) throws IOException {
        return baseStore.isAuxObjectCached(auxItemTag);
    }

    @Override
    public long getAuxObjectSize(String auxItemTag) throws IOException {
        return baseStore.getAuxObjectSize(auxItemTag);
    }

    @Override
    public Path getAuxObjectAsPath(String auxItemTag) throws IOException {
        return baseStore.getAuxObjectAsPath(auxItemTag);
    }

    @Override
    public void backupAsAux(String auxItemTag) throws IOException {
        baseStore.backupAsAux(auxItemTag);
    }

    @Override
    public void revertBackupAsAux(String auxItemTag) throws IOException {
        baseStore.revertBackupAsAux(auxItemTag);
    }

    @Override
    // this method copies a local filesystem Path into this DataAccess Auxiliary
    // location:
    public void savePathAsAux(Path fileSystemPath, String auxItemTag) throws IOException {
        baseStore.savePathAsAux(fileSystemPath, auxItemTag);
    }

    @Override
    public void saveInputStreamAsAux(InputStream inputStream, String auxItemTag, Long filesize) throws IOException {
        baseStore.saveInputStreamAsAux(inputStream, auxItemTag, filesize);
    }

    /**
     * @param inputStream InputStream we want to save
     * @param auxItemTag  String representing this Auxiliary type ("extension")
     * @throws IOException if anything goes wrong.
     */
    @Override
    public void saveInputStreamAsAux(InputStream inputStream, String auxItemTag) throws IOException {
        baseStore.saveInputStreamAsAux(inputStream, auxItemTag);
    }

    @Override
    public List<String> listAuxObjects() throws IOException {
        return baseStore.listAuxObjects();
    }

    @Override
    public void deleteAuxObject(String auxItemTag) throws IOException {
        baseStore.deleteAuxObject(auxItemTag);
    }

    @Override
    public void deleteAllAuxObjects() throws IOException {
        baseStore.deleteAllAuxObjects();
    }

    @Override
    public String getStorageLocation() throws IOException {
        String fullStorageLocation = dvObject.getStorageIdentifier();
        logger.fine("storageidentifier: " + fullStorageLocation);
        fullStorageLocation = fullStorageLocation.substring(fullStorageLocation.lastIndexOf("://") + 3);
        fullStorageLocation = fullStorageLocation.substring(0, fullStorageLocation.indexOf("//"));
        if (this.getDvObject() instanceof Dataset) {
            fullStorageLocation = this.getDataset().getAuthorityForFileStorage() + "/"
                    + this.getDataset().getIdentifierForFileStorage() + "/" + fullStorageLocation;
        } else if (this.getDvObject() instanceof DataFile) {
            fullStorageLocation = this.getDataFile().getOwner().getAuthorityForFileStorage() + "/"
                    + this.getDataFile().getOwner().getIdentifierForFileStorage() + "/" + fullStorageLocation;
        } else if (dvObject instanceof Dataverse) {
            throw new IOException("HttpOverlayAccessIO: Dataverses are not a supported dvObject");
        }
        return fullStorageLocation;
    }

    @Override
    public Path getFileSystemPath() throws UnsupportedDataAccessOperationException {
        throw new UnsupportedDataAccessOperationException(
                "HttpOverlayAccessIO: this is a remote DataAccess IO object, it has no local filesystem path associated with it.");
    }

    @Override
    public boolean exists() {
        logger.fine("Exists called");
        return (getSizeFromHttpHeader() != -1);
    }

    @Override
    public WritableByteChannel getWriteChannel() throws UnsupportedDataAccessOperationException {
        throw new UnsupportedDataAccessOperationException(
                "HttpOverlayAccessIO: there are no write Channels associated with S3 objects.");
    }

    @Override
    public OutputStream getOutputStream() throws UnsupportedDataAccessOperationException {
        throw new UnsupportedDataAccessOperationException(
                "HttpOverlayAccessIO: there are no output Streams associated with S3 objects.");
    }

    @Override
    public InputStream getAuxFileAsInputStream(String auxItemTag) throws IOException {
        return baseStore.getAuxFileAsInputStream(auxItemTag);
    }

    @Override
    public boolean downloadRedirectEnabled() {
        String optionValue = System.getProperty("dataverse.files." + this.driverId + ".download-redirect");
        if ("true".equalsIgnoreCase(optionValue)) {
            return true;
        }
        return false;
    }

    public String generateTemporaryDownloadUrl() throws IOException {
        String secretKey = System.getProperty("dataverse.files." + this.driverId + ".secretkey");
        if (secretKey == null) {
            return baseUrl + "/" + urlPath;
        } else {
            return UrlSignerUtil.signUrl(baseUrl + "/" + urlPath, getUrlExpirationMinutes(), null, "GET", secretKey);
        }
    }

    int getUrlExpirationMinutes() {
        String optionValue = System.getProperty("dataverse.files." + this.driverId + ".url-expiration-minutes");
        if (optionValue != null) {
            Integer num;
            try {
                num = Integer.parseInt(optionValue);
            } catch (NumberFormatException ex) {
                num = null;
            }
            if (num != null) {
                return num;
            }
        }
        return 60;
    }

    private void configureStores(DataAccessRequest req, String driverId, String storageLocation) throws IOException {
        baseUrl = System.getProperty("dataverse.files." + this.driverId + ".baseUrl");

        if (baseStore == null) {
            String baseDriverId = System.getProperty("dataverse.files." + driverId + ".baseStore");
            String fullStorageLocation = null;
            String baseDriverType = System.getProperty("dataverse.files." + baseDriverId + ".type");
            if(dvObject  instanceof Dataset) {
                baseStore = DataAccess.getStorageIO(dvObject, req, baseDriverId);
            } else {
                if (this.getDvObject() != null) {
                    fullStorageLocation = getStorageLocation();

                    // S3 expects <id>://<bucketname>/<key>
                    switch (baseDriverType) {
                    case "s3":
                        fullStorageLocation = baseDriverId + "://"
                                + System.getProperty("dataverse.files." + baseDriverId + ".bucket-name") + "/"
                                + fullStorageLocation;
                        break;
                    case "file":
                        fullStorageLocation = baseDriverId + "://"
                                + System.getProperty("dataverse.files." + baseDriverId + ".directory") + "/"
                                + fullStorageLocation;
                        break;
                    default:
                        logger.warning("Not Implemented: HTTPOverlay store with base store type: "
                                + System.getProperty("dataverse.files." + baseDriverId + ".type"));
                        throw new IOException("Not implemented");
                    }

                } else if (storageLocation != null) {
                    // <httpDriverId>://<baseStorageIdentifier>//<baseUrlPath>
                    String storageId = storageLocation.substring(storageLocation.indexOf("://" + 3));
                    fullStorageLocation = storageId.substring(0, storageId.indexOf("//"));

                    switch (baseDriverType) {
                    case "s3":
                        fullStorageLocation = baseDriverId + "://"
                                + System.getProperty("dataverse.files." + baseDriverId + ".bucket-name") + "/"
                                + fullStorageLocation;
                        break;
                    case "file":
                        fullStorageLocation = baseDriverId + "://"
                                + System.getProperty("dataverse.files." + baseDriverId + ".directory") + "/"
                                + fullStorageLocation;
                        break;
                    default:
                        logger.warning("Not Implemented: HTTPOverlay store with base store type: "
                                + System.getProperty("dataverse.files." + baseDriverId + ".type"));
                        throw new IOException("Not implemented");
                    }
                }
                baseStore = DataAccess.getDirectStorageIO(fullStorageLocation);
            }
            if (baseDriverType.contentEquals("s3")) {
                ((S3AccessIO<?>) baseStore).setMainDriver(false);
            }
        }
    }

    public CloseableHttpClient getSharedHttpClient() {
        if (httpclient == null) {
            try {
                initHttpPool();
                httpclient = HttpClients.custom().setConnectionManager(cm).setDefaultRequestConfig(config).build();

            } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
                logger.warning(ex.getMessage());
            }
        }
        return httpclient;
    }

    private void initHttpPool() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        if (trustCerts) {
            // use the TrustSelfSignedStrategy to allow Self Signed Certificates
            SSLContext sslContext;
            SSLConnectionSocketFactory connectionFactory;

            sslContext = SSLContextBuilder.create().loadTrustMaterial(new TrustAllStrategy()).build();
            // create an SSL Socket Factory to use the SSLContext with the trust self signed
            // certificate strategy
            // and allow all hosts verifier.
            connectionFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("https", connectionFactory).build();
            cm = new PoolingHttpClientConnectionManager(registry);
        } else {
            cm = new PoolingHttpClientConnectionManager();
        }
        cm.setDefaultMaxPerRoute(httpConcurrency);
        cm.setMaxTotal(httpConcurrency > 20 ? httpConcurrency : 20);
    }

    @Override
    public void savePath(Path fileSystemPath) throws IOException {
        throw new UnsupportedDataAccessOperationException(
                "HttpOverlayAccessIO: savePath() not implemented in this storage driver.");

    }

    @Override
    public void saveInputStream(InputStream inputStream) throws IOException {
        throw new UnsupportedDataAccessOperationException(
                "HttpOverlayAccessIO: saveInputStream() not implemented in this storage driver.");

    }

    @Override
    public void saveInputStream(InputStream inputStream, Long filesize) throws IOException {
        throw new UnsupportedDataAccessOperationException(
                "HttpOverlayAccessIO: saveInputStream(InputStream, Long) not implemented in this storage driver.");

    }

}
