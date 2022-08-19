package edu.harvard.iq.dataverse.util.json;

import com.google.gson.Gson;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import edu.harvard.iq.dataverse.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.DataFileCategory;
import edu.harvard.iq.dataverse.DataTable;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetField;
import edu.harvard.iq.dataverse.DatasetFieldConstant;
import edu.harvard.iq.dataverse.DatasetFieldCompoundValue;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldType;
import edu.harvard.iq.dataverse.DatasetFieldValue;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.DataverseTheme;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.MetadataBlockServiceBean;
import edu.harvard.iq.dataverse.TermsOfUseAndAccess;
import edu.harvard.iq.dataverse.api.Util;
import edu.harvard.iq.dataverse.api.dto.FieldDTO;
import edu.harvard.iq.dataverse.authorization.groups.impl.ipaddress.IpGroup;
import edu.harvard.iq.dataverse.authorization.groups.impl.ipaddress.ip.IpAddress;
import edu.harvard.iq.dataverse.authorization.groups.impl.ipaddress.ip.IpAddressRange;
import edu.harvard.iq.dataverse.authorization.groups.impl.maildomain.MailDomainGroup;
import edu.harvard.iq.dataverse.datasetutility.OptionalFileParams;
import edu.harvard.iq.dataverse.datavariable.DataVariable;
import edu.harvard.iq.dataverse.datavariable.SummaryStatistic;
import edu.harvard.iq.dataverse.datavariable.VariableCategory;
import edu.harvard.iq.dataverse.harvest.client.HarvestingClient;
import edu.harvard.iq.dataverse.license.License;
import edu.harvard.iq.dataverse.license.LicenseServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.workflow.Workflow;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepData;
import org.apache.commons.validator.routines.DomainValidator;
import java.io.StringReader;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import org.apache.commons.lang.StringUtils;

/**
 * Parses JSON objects into domain objects.
 *
 * @author michael
 */
public class JsonParser {

    private static final Logger logger = Logger.getLogger(JsonParser.class.getCanonicalName());
    static XStream xstream = new XStream(new JsonHierarchicalStreamDriver());
    DatasetFieldServiceBean datasetFieldSvc;
    MetadataBlockServiceBean blockService;
    SettingsServiceBean settingsService;
    LicenseServiceBean licenseService;
    
    /**
     * if lenient, we will accept alternate spellings for controlled vocabulary values
     */
    boolean lenient = false;  

    @Deprecated
    public JsonParser(DatasetFieldServiceBean datasetFieldSvc, MetadataBlockServiceBean blockService, SettingsServiceBean settingsService) {
        this.datasetFieldSvc = datasetFieldSvc;
        this.blockService = blockService;
        this.settingsService = settingsService;
    }

    public JsonParser(DatasetFieldServiceBean datasetFieldSvc, MetadataBlockServiceBean blockService, SettingsServiceBean settingsService, LicenseServiceBean licenseService) {
        this.datasetFieldSvc = datasetFieldSvc;
        this.blockService = blockService;
        this.settingsService = settingsService;
        this.licenseService = licenseService;
    }

    public JsonParser() {
        this( null,null,null );
    }
    
    public boolean isLenient() {
        return lenient;
    }

    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    public Dataverse parseDataverse(JsonObject jobj) throws JsonParseException {
        Dataverse dv = new Dataverse();

        /**
         * @todo Instead of this getMandatoryString method we should run the
         * String through ConstraintValidator. See EMailValidatorTest and
         * EMailValidator for examples. That way we can check not only if it's
         * required or not but other bean validation rules such as "must match
         * this regex".
         */
        dv.setAlias(getMandatoryString(jobj, "alias"));
        dv.setName(getMandatoryString(jobj, "name"));
        dv.setDescription(jobj.getString("description", null));
        dv.setPermissionRoot(jobj.getBoolean("permissionRoot", false));
        dv.setFacetRoot(jobj.getBoolean("facetRoot", false));
        dv.setAffiliation(jobj.getString("affiliation", null));
      
        if (jobj.containsKey("dataverseContacts")) {
            JsonArray dvContacts = jobj.getJsonArray("dataverseContacts");
            int i = 0;
            List<DataverseContact> dvContactList = new LinkedList<>();
            for (JsonValue jsv : dvContacts) {
                DataverseContact dvc = new DataverseContact(dv);
                dvc.setContactEmail(getMandatoryString((JsonObject) jsv, "contactEmail"));
                dvc.setDisplayOrder(i++);
                dvContactList.add(dvc);
            }
            dv.setDataverseContacts(dvContactList);
        }
        
        if (jobj.containsKey("theme")) {
            DataverseTheme theme = parseDataverseTheme(jobj.getJsonObject("theme"));
            dv.setDataverseTheme(theme);
            theme.setDataverse(dv);
        }

        dv.setDataverseType(Dataverse.DataverseType.UNCATEGORIZED); // default
        if (jobj.containsKey("dataverseType")) {
            for (Dataverse.DataverseType dvtype : Dataverse.DataverseType.values()) {
                if (dvtype.name().equals(jobj.getString("dataverseType"))) {
                    dv.setDataverseType(dvtype);
                }
            }
        }

        /*  We decided that subject is not user set, but gotten from the subject of the dataverse's
            datasets - leavig this code in for now, in case we need to go back to it at some point
        
        if (jobj.containsKey("dataverseSubjects")) {
            List<ControlledVocabularyValue> dvSubjectList = new LinkedList<>();
            DatasetFieldType subjectType = datasetFieldSvc.findByName(DatasetFieldConstant.subject);
            List<JsonString> subjectList = jobj.getJsonArray("dataverseSubjects").getValuesAs(JsonString.class);
            if (subjectList.size() > 0) {
                // check first value for "all"
                if (subjectList.get(0).getString().trim().toLowerCase().equals("all")) {
                    dvSubjectList.addAll(subjectType.getControlledVocabularyValues());
                } else {
                    for (JsonString subject : subjectList) {
                        ControlledVocabularyValue cvv = datasetFieldSvc.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(subjectType, subject.getString(),lenient);
                        if (cvv != null) {
                            dvSubjectList.add(cvv);
                        } else {
                            throw new JsonParseException("Value '" + subject.getString() + "' does not exist in type '" + subjectType.getName() + "'");
                        }
                    }
                }
            }
            dv.setDataverseSubjects(dvSubjectList);
        }
        */
                
        return dv;
    }
    
    public DataverseTheme parseDataverseTheme(JsonObject obj) {

        DataverseTheme theme = new DataverseTheme();

        if (obj.containsKey("backgroundColor")) {
            theme.setBackgroundColor(obj.getString("backgroundColor", null));
        }
        if (obj.containsKey("linkColor")) {
            theme.setLinkColor(obj.getString("linkColor", null));
        }
        if (obj.containsKey("linkUrl")) {
            theme.setLinkUrl(obj.getString("linkUrl", null));
        }
        if (obj.containsKey("logo")) {
            theme.setLogo(obj.getString("logo", null));
        }
        if (obj.containsKey("logoAlignment")) {
            String align = obj.getString("logoAlignment");
            if (align.equalsIgnoreCase("left")) {
                theme.setLogoAlignment(DataverseTheme.Alignment.LEFT);
            }
            if (align.equalsIgnoreCase("right")) {
                theme.setLogoAlignment(DataverseTheme.Alignment.RIGHT);
            }
            if (align.equalsIgnoreCase("center")) {
                theme.setLogoAlignment(DataverseTheme.Alignment.CENTER);
            }
        }
        if (obj.containsKey("logoBackgroundColor")) {
            theme.setLogoBackgroundColor(obj.getString("logoBackgroundColor", null));
        }
        if (obj.containsKey("logoFormat")) {
            String format = obj.getString("logoFormat");
            if (format.equalsIgnoreCase("square")) {
                theme.setLogoFormat(DataverseTheme.ImageFormat.SQUARE);
            }
            if (format.equalsIgnoreCase("rectangle")) {
                theme.setLogoFormat(DataverseTheme.ImageFormat.RECTANGLE);
            }
        }
        if (obj.containsKey("tagline")) {
            theme.setTagline(obj.getString("tagline", null));
        }
        if (obj.containsKey("textColor")) {
            theme.setTextColor(obj.getString("textColor", null));
        }

        return theme;
    }

    private static String getMandatoryString(JsonObject jobj, String name) throws JsonParseException {
        if (jobj.containsKey(name)) {
            return jobj.getString(name);
        }
        throw new JsonParseException("Field " + name + " is mandatory");
    }

    public IpGroup parseIpGroup(JsonObject obj) {
        IpGroup retVal = new IpGroup();

        if (obj.containsKey("id")) {
            retVal.setId(Long.valueOf(obj.getInt("id")));
        }
        retVal.setDisplayName(obj.getString("name", null));
        retVal.setDescription(obj.getString("description", null));
        retVal.setPersistedGroupAlias(obj.getString("alias", null));

        if ( obj.containsKey("ranges") ) {
            obj.getJsonArray("ranges").stream()
                    .filter( jv -> jv.getValueType()==JsonValue.ValueType.ARRAY )
                    .map( jv -> (JsonArray)jv )
                    .forEach( rr -> {
                        retVal.add(
                            IpAddressRange.make(IpAddress.valueOf(rr.getString(0)),
                                                IpAddress.valueOf(rr.getString(1))));
            });
        }
        if ( obj.containsKey("addresses") ) {
            obj.getJsonArray("addresses").stream()
                    .map( jsVal -> IpAddress.valueOf(((JsonString)jsVal).getString()) )
                    .map( addr -> IpAddressRange.make(addr, addr) )
                    .forEach( retVal::add );
        }

        return retVal;
    }
    
    public MailDomainGroup parseMailDomainGroup(JsonObject obj) throws JsonParseException {
        MailDomainGroup grp = new MailDomainGroup();
        
        if (obj.containsKey("id")) {
            grp.setId(obj.getJsonNumber("id").longValue());
        }
        grp.setDisplayName(getMandatoryString(obj, "name"));
        grp.setDescription(obj.getString("description", null));
        grp.setPersistedGroupAlias(getMandatoryString(obj, "alias"));
        grp.setIsRegEx(obj.getBoolean("regex", false));
        if ( obj.containsKey("domains") ) {
            List<String> domains =
                Optional.ofNullable(obj.getJsonArray("domains"))
                    .orElse(Json.createArrayBuilder().build())
                    .getValuesAs(JsonString.class)
                    .stream()
                    .map(JsonString::getString)
                    // only validate if this group hasn't regex support enabled
                    .filter(d -> (grp.isRegEx() || DomainValidator.getInstance().isValid(d)))
                    .collect(Collectors.toList());
            if (domains.isEmpty())
                throw new JsonParseException("Field domains may not be an empty array or contain invalid domains. Enabled regex support?");
            grp.setEmailDomains(domains);
        } else {
            throw new JsonParseException("Field domains is mandatory.");
        }
        
        return grp;
    }

    public static <E extends Enum<E>> List<E> parseEnumsFromArray(JsonArray enumsArray, Class<E> enumClass) throws JsonParseException {
        final List<E> enums = new LinkedList<>();

        for (String name : enumsArray.getValuesAs(JsonString::getString)) {
            enums.add(Enum.valueOf(enumClass, name));
        }
        return enums;
    }

    public DatasetVersion parseDatasetVersion(JsonObject obj) throws JsonParseException {
        logger.log(Level.INFO, "1-arg-parseDatasetVersion:obj is called");
//        logger.log(Level.INFO, "obj:1-arg-parseDatasetVersion={0}",xstream.toXML(obj));
        logger.log(Level.INFO, "within 1-arg-parseDatasetVersion, 2-args-version is called");
        return parseDatasetVersion(obj, new DatasetVersion());
    }

    public Dataset parseDataset(JsonObject obj) throws JsonParseException {
        logger.log(Level.INFO, "parseDataset is called");
//        logger.log(Level.INFO, "obj: parseDataset={0}",xstream.toXML(obj));
        Dataset dataset = new Dataset();

        dataset.setAuthority(obj.getString("authority", null) == null ? settingsService.getValueForKey(SettingsServiceBean.Key.Authority) : obj.getString("authority"));
        dataset.setProtocol(obj.getString("protocol", null) == null ? settingsService.getValueForKey(SettingsServiceBean.Key.Protocol) : obj.getString("protocol"));
        dataset.setIdentifier(obj.getString("identifier",null));
        String mdl = obj.getString("metadataLanguage",null);
        if(mdl==null || settingsService.getBaseMetadataLanguageMap(new HashMap<String,String>(), true).containsKey(mdl)) {
          dataset.setMetadataLanguage(mdl);
        }else {
            throw new JsonParseException("Specified metadatalanguage not allowed.");
        }
        
        DatasetVersion dsv = new DatasetVersion(); 
        dsv.setDataset(dataset);
        logger.log(Level.INFO, "calling 2-args-parseDatasetVersion method");
        dsv = parseDatasetVersion(obj.getJsonObject("datasetVersion"), dsv);
        List<DatasetVersion> versions = new ArrayList<>(1);
        versions.add(dsv);

        dataset.setVersions(versions);
        return dataset;
    }

    public DatasetVersion parseDatasetVersion(JsonObject obj, DatasetVersion dsv) throws JsonParseException {
            logger.log(Level.INFO, "2-args-parseDatasetVersion is called");
//            logger.log(Level.INFO, "obj:2-args-parseDatasetVersion={0}",xstream.toXML(obj));
        try {

            String archiveNote = obj.getString("archiveNote", null);
            if (archiveNote != null) {
                dsv.setArchiveNote(archiveNote);
            }

            dsv.setDeaccessionLink(obj.getString("deaccessionLink", null));
            int versionNumberInt = obj.getInt("versionNumber", -1);
            Long versionNumber = null;
            if (versionNumberInt !=-1) {
                versionNumber = new Long(versionNumberInt);
            }
            dsv.setVersionNumber(versionNumber);
            dsv.setMinorVersionNumber(parseLong(obj.getString("minorVersionNumber", null)));
            // if the existing datasetversion doesn not have an id
            // use the id from the json object.
            if (dsv.getId()==null) {
                 dsv.setId(parseLong(obj.getString("id", null)));
            }
           
            String versionStateStr = obj.getString("versionState", null);
            if (versionStateStr != null) {
                dsv.setVersionState(DatasetVersion.VersionState.valueOf(versionStateStr));
            }
            dsv.setReleaseTime(parseDate(obj.getString("releaseDate", null)));
            dsv.setLastUpdateTime(parseTime(obj.getString("lastUpdateTime", null)));
            dsv.setCreateTime(parseTime(obj.getString("createTime", null)));
            dsv.setArchiveTime(parseTime(obj.getString("archiveTime", null)));
            dsv.setUNF(obj.getString("UNF", null));
            // Terms of Use related fields
            TermsOfUseAndAccess terms = new TermsOfUseAndAccess();
            License license = parseLicense(obj.getString("license", null));
            if (license == null) {
                terms.setLicense(license);
                terms.setTermsOfUse(obj.getString("termsOfUse", null));
                terms.setConfidentialityDeclaration(obj.getString("confidentialityDeclaration", null));
                terms.setSpecialPermissions(obj.getString("specialPermissions", null));
                terms.setRestrictions(obj.getString("restrictions", null));
                terms.setCitationRequirements(obj.getString("citationRequirements", null));
                terms.setDepositorRequirements(obj.getString("depositorRequirements", null));
                terms.setConditions(obj.getString("conditions", null));
                terms.setDisclaimer(obj.getString("disclaimer", null));
            } else {
                terms.setLicense(license);
            }
            terms.setTermsOfAccess(obj.getString("termsOfAccess", null));
            terms.setDataAccessPlace(obj.getString("dataAccessPlace", null));
            terms.setOriginalArchive(obj.getString("originalArchive", null));
            terms.setAvailabilityStatus(obj.getString("availabilityStatus", null));
            terms.setContactForAccess(obj.getString("contactForAccess", null));
            terms.setSizeOfCollection(obj.getString("sizeOfCollection", null));
            terms.setStudyCompletion(obj.getString("studyCompletion", null));
            terms.setFileAccessRequest(obj.getBoolean("fileAccessRequest", false));
            dsv.setTermsOfUseAndAccess(terms);
            terms.setDatasetVersion(dsv);
            JsonObject metadataBlocks = obj.getJsonObject("metadataBlocks");
            if (metadataBlocks == null){
                throw new JsonParseException(BundleUtil.getStringFromBundle("jsonparser.error.metadatablocks.not.found"));
            }
            dsv.setDatasetFields(parseMetadataBlocks(metadataBlocks));

            JsonArray filesJson = obj.getJsonArray("files");
            if (filesJson == null) {
                filesJson = obj.getJsonArray("fileMetadatas");
            }
            if (filesJson != null) {
                logger.log(Level.INFO, "parseFiles to be called");
                dsv.setFileMetadatas(parseFiles(filesJson, dsv));
            }
            return dsv;
        } catch (ParseException ex) {      
            throw new JsonParseException(BundleUtil.getStringFromBundle("jsonparser.error.parsing.date", Arrays.asList(ex.getMessage())) , ex);
        } catch (NumberFormatException ex) {
            throw new JsonParseException(BundleUtil.getStringFromBundle("jsonparser.error.parsing.number", Arrays.asList(ex.getMessage())), ex);
        }
    }
    
    private edu.harvard.iq.dataverse.license.License parseLicense(String licenseNameOrUri) throws JsonParseException {
        if (licenseNameOrUri == null){
            boolean safeDefaultIfKeyNotFound = true;
            if (settingsService.isTrueForKey(SettingsServiceBean.Key.AllowCustomTermsOfUse, safeDefaultIfKeyNotFound)){
                return null;
            } else {
                return licenseService.getDefault();
            }
        }
        License license = licenseService.getByNameOrUri(licenseNameOrUri);
        if (license == null) throw new JsonParseException("Invalid license: " + licenseNameOrUri);
        return license;
    }

    public List<DatasetField> parseMetadataBlocks(JsonObject json) throws JsonParseException {
        logger.log(Level.INFO, "within parseMetadataBlocks");
        logger.log(Level.FINE, "parseMetadataBlocks:json={0}",xstream.toXML(json));
        
        Set<String> keys = json.keySet();
//        logger.log(Level.INFO, "keys={0}", xstream.toXML(keys));
        List<DatasetField> fields = new LinkedList<>();

        for (String blockName : keys) {
            JsonObject blockJson = json.getJsonObject(blockName);
            JsonArray fieldsJson = blockJson.getJsonArray("fields");
            fields.addAll(parseFieldsFromArray(fieldsJson, true));
        }
        return fields;
    }
    
    public List<DatasetField> parseMultipleFields(JsonObject json) throws JsonParseException {
        JsonArray fieldsJson = json.getJsonArray("fields");
        List<DatasetField> fields = parseFieldsFromArray(fieldsJson, false);
        return fields;
    }
    
    public List<DatasetField> parseMultipleFieldsForDelete(JsonObject json) throws JsonParseException {
        List<DatasetField> fields = new LinkedList<>();
        for (JsonObject fieldJson : json.getJsonArray("fields").getValuesAs(JsonObject.class)) {
            fields.add(parseFieldForDelete(fieldJson));
        }
        return fields;
    }
    
    private List<DatasetField> parseFieldsFromArray(JsonArray fieldsArray, Boolean testType) throws JsonParseException {
        logger.log(Level.INFO, "entering parseFieldsFromArray method:testType={0}", testType);
            List<DatasetField> fields = new LinkedList<>();
            for (JsonObject fieldJson : fieldsArray.getValuesAs(JsonObject.class)) {
                try {
                    DatasetField field = parseField(fieldJson, testType);
                    if (field != null) {
                        fields.add(field);
                    }
                } catch (CompoundVocabularyException ex) {
                    DatasetFieldType fieldType = datasetFieldSvc.findByNameOpt(fieldJson.getString("typeName", ""));
                    if (lenient && (DatasetFieldConstant.geographicCoverage).equals(fieldType.getName())) {
                        fields.add(remapGeographicCoverage( ex));                       
                    } else {
                        // if not lenient mode, re-throw exception
                        throw ex;
                    }
                } 

            }
        return fields;
        
    }
    
    public List<FileMetadata> parseFiles(JsonArray metadatasJson, DatasetVersion dsv) throws JsonParseException {
        logger.log(Level.INFO, "parseFiles is called");
        List<FileMetadata> fileMetadatas = new LinkedList<>();
        if (metadatasJson != null) {
            for (JsonObject filemetadataJson : metadatasJson.getValuesAs(JsonObject.class)) {
                String label = filemetadataJson.getString("label");
                boolean restricted = filemetadataJson.getBoolean("restricted", false);
                String directoryLabel = filemetadataJson.getString("directoryLabel", null);
                String description = filemetadataJson.getString("description", null);

                FileMetadata fileMetadata = new FileMetadata();
                fileMetadata.setLabel(label);
                fileMetadata.setRestricted(restricted);
                fileMetadata.setDirectoryLabel(directoryLabel);
                fileMetadata.setDescription(description);
                fileMetadata.setDatasetVersion(dsv);
                
                if ( filemetadataJson.containsKey("dataFile") ) {
                    DataFile dataFile = parseDataFile(filemetadataJson.getJsonObject("dataFile"));
                    dataFile.getFileMetadatas().add(fileMetadata);
                    if (dsv.getDataset() == null) {
                        logger.log(Level.INFO, "datasetVersion does not have a dataset; attach a dataset to dsv");
                        Dataset dataset = new Dataset();
                        dsv.setDataset(dataset);
                    }
                    // the following line may cause NullpointerException 
                    // if a Dataset is not attached to dsv
                    // therefore a null test is necessary
                    dataFile.setOwner(dsv.getDataset());
                    fileMetadata.setDataFile(dataFile);
                    if (dsv.getDataset() != null) {
                        if (dsv.getDataset().getFiles() == null) {
                            dsv.getDataset().setFiles(new ArrayList<>());
                        }
                        dsv.getDataset().getFiles().add(dataFile);
                    }
                }
                
                fileMetadatas.add(fileMetadata);
                fileMetadata.setCategories(getCategories(filemetadataJson, dsv.getDataset()));
            }
        }

        return fileMetadatas;
    }
    
    public DataFile parseDataFile(JsonObject datafileJson) {
        logger.log(Level.INFO, "parseDataFile is called");
//        logger.log(Level.INFO, "datafileJson={0}",xstream.toXML(datafileJson));
        DataFile dataFile = new DataFile();
        
        Timestamp timestamp = new Timestamp(new Date().getTime());
        dataFile.setCreateDate(timestamp);
        dataFile.setModificationTime(timestamp);
        dataFile.setPermissionModificationTime(timestamp);
        
        // as of version 4.9.4, missing datafile-items that exist in JsonPrinter
        // persistentId
        // pidURL
        
        if ( datafileJson.containsKey("filesize") ) {
            dataFile.setFilesize(datafileJson.getJsonNumber("filesize").longValueExact());
        }
        
        String contentType = datafileJson.getString("contentType", null);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        String storageIdentifier = datafileJson.getString("storageIdentifier", " ");

        // filename: new
        String filename = datafileJson.getString("filename", null);
        // filesize(duplicated; see the above)
//        JsonNumber filesizejsn = datafileJson.getJsonNumber("filesize");
//        Long filesize = null; 
//        if (filesizejsn != null){
//            filesize = datafileJson.getJsonNumber("filesize").longValue();
//        }
/*
        // originalFileFormat: new
        String originalFileFormat = datafileJson.getString("originalFileFormat", null);
        // originalFormatLabel: new
        String originalFormatLabel = datafileJson.getString("originalFormatLabel", null);
        // UNF: new
        String UNF = datafileJson.getString("UNF", null);
        // md5: new
        String MD5 = datafileJson.getString("md5", null);
*/
        
        JsonObject checksum = datafileJson.getJsonObject("checksum");
        if (checksum != null) {
            // newer style that allows for SHA-1 rather than MD5
            /**
             * @todo Add more error checking. Do we really expect people to set
             * file metadata without uploading files? Some day we'd like to work
             * on a "native" API that allows for multipart upload of the JSON
             * describing the files (this "parseDataFile" method) and the bits
             * of the files themselves. See
             * https://github.com/IQSS/dataverse/issues/1612
             */
            String type = checksum.getString("type");
            if (type != null) {
                String value = checksum.getString("value");
                if (value != null) {
                    try {
                        dataFile.setChecksumType(DataFile.ChecksumType.fromString(type));
                        dataFile.setChecksumValue(value);
                    } catch (IllegalArgumentException ex) {
                        logger.info("Invalid");
                    }
                }
            }
        } else {
            // older, MD5 logic, still her for backward compatibility
            String md5 = datafileJson.getString("md5", null);
            if (md5 == null) {
                md5 = "unknown";
            }
            dataFile.setChecksumType(DataFile.ChecksumType.MD5);
            dataFile.setChecksumValue(md5);
        }

        // TODO: 
        // unf (if available)... etc.?
        
        dataFile.setContentType(contentType);
        dataFile.setStorageIdentifier(storageIdentifier);
//        if (filesize != null){
//            dataFile.setFilesize(filesize);
//        }
        // dataFile.setNotaryServiceBound(isNotaryServiceBound);
        logger.log(Level.INFO, "parsing DataTable from parseDataFile");
        // parse DataTable
        JsonArray dataTablesJson = datafileJson.getJsonArray("dataTables");
        if ((dataTablesJson != null ) && (!dataTablesJson.isEmpty())){
            logger.log(Level.INFO, "dataTablesJson:size={0}", dataTablesJson.size());
            // get parsing results of a DataTable
            List<DataTable> dataTables = parseDataTables(dataTablesJson);
            logger.log(Level.INFO, "dataTables:size={0}", dataTables.size());
//            logger.log(Level.FINE, "returned dataTables={0}", xstream.toXML(dataTables));
            dataFile.setDataTables(dataTables);
            dataFile.setDataTable(dataTables.get(0));
            dataTables.get(0).setDataFile(dataFile);
//            dataTables.get(0).setOriginalFileFormat(originalFileFormat);
            
        } else {
            logger.log(Level.WARNING, "dataTablesJson is null or empty");
        }
//        logger.log(Level.INFO, "dataFile: parseDataFile={0}",xstream.toXML(dataFile));
        return dataFile;
    }
    
    
    
    public List<DataTable> parseDataTables(JsonArray dataTablesJson){
        logger.log(Level.INFO, "parseDataTables is called");
        List<DataTable> dataTables = new LinkedList<>();
        if ((dataTablesJson !=null) && (!dataTablesJson.isEmpty())){
            logger.log(Level.INFO, "dataTables is not empty");
            for (JsonObject dataTableJsonL : dataTablesJson.getValuesAs(JsonObject.class)){
                JsonObject dataTableJson = dataTableJsonL.getJsonObject("dataTable");
                DataTable dataTable = new DataTable();
                // capture scalar items
                // varQuantity
                long varQuantity =  dataTableJson.getJsonNumber("varQuantity").longValue();
                dataTable.setVarQuantity(varQuantity);
                // caseQuantity
                long caseQuantity = dataTableJson.getJsonNumber("caseQuantity").longValue();
                dataTable.setCaseQuantity(caseQuantity);
                
                // must-be-synced with datatable
                // originalfileformat: string
                String originalfileformat = dataTableJson.getString("originalFileFormat");
                dataTable.setOriginalFileFormat(originalfileformat);
                // originalfilename: string 
                String originalfilename = dataTableJson.getString("originalFileName");
                dataTable.setOriginalFileName(originalfilename);
                // originalfilesize: Long
                long originalfilesize = dataTableJson.getJsonNumber("originalFileSize").longValue();
                dataTable.setOriginalFileSize(originalfilesize);
                // UNF
                String UNF = dataTableJson.getString("UNF", null);
                dataTable.setUnf(UNF);
                // call the method for pasring dataVariables array
                List<DataVariable> dataVariables = parseDataVariables(dataTableJson.getJsonArray("dataVariables"), dataTable);
                logger.log(Level.INFO, "returned dataVariables list: size={0}", dataVariables.size());
                
                dataTable.setDataVariables(dataVariables);
                dataTables.add(dataTable);
                logger.log(Level.INFO, " dataTables: current size={0}", dataTables.size());
            }
        }
        logger.log(Level.INFO, "dataTables: size(final)={0}", dataTables.size());
        return dataTables;
    }
    
    
    // note: the following method has not yet implemented a few of processXXX-
    // calls in DataTableImportDDI#processVar
    
    public List<DataVariable> parseDataVariables(JsonArray dataVariablesJson, DataTable dataTable){
        logger.log(Level.INFO, "parseDataVariables is called");
        List<DataVariable> dataVariables = new LinkedList<>();
        if ((dataVariablesJson != null) && (!dataVariablesJson.isEmpty())) {
            logger.log(Level.INFO, "dataVariablesJson is not empty:size={0}", dataVariablesJson.size());
            for (JsonObject dataVariableJson: dataVariablesJson.getValuesAs(JsonObject.class)){
                DataVariable dataVariable = new DataVariable();
                // capture scalar itemse.
                // name
                dataVariable.setName(dataVariableJson.getString("name", null));
                // label
                dataVariable.setLabel(dataVariableJson.getString("label", null));
                // weighted
                dataVariable.setWeighted(dataVariableJson.getBoolean("weighted", false));
                String variableIntervalType= dataVariableJson.getString("variableIntervalType", null);
                if (variableIntervalType!=null){
                    String variableIntervalTypeFinal = variableIntervalType.toUpperCase();
                    if (variableIntervalType.equals("contin")){
                        variableIntervalTypeFinal="CONTINUOUS";
                    }
                    dataVariable.setInterval(DataVariable.VariableInterval.valueOf(variableIntervalTypeFinal));
                }
                // variableFormatType
                String variableFormatType = dataVariableJson.getString("variableFormatType", null);
                logger.log(Level.INFO, "variableFormatType={0}", variableFormatType);
                if (variableFormatType!=null){
                    dataVariable.setType(DataVariable.VariableType.valueOf(variableFormatType));
                }
                logger.log(Level.INFO, "type={0}", dataVariable.getType());
                // orderedFactor
                dataVariable.setOrderedCategorical(dataVariableJson.getBoolean("orderedFactor", false));
                // facotr 
                dataVariable.setFactor(dataVariableJson.getBoolean("factor", false));
                // fileOrder
                dataVariable.setFileOrder(dataVariableJson.getInt("fileOrder"));
                
                // summaryStatistics
                dataVariable.setSummaryStatistics(parseSummaryStatistics(dataVariableJson.getJsonObject("summaryStatistics"), dataVariable));
                // variableCategories
                dataVariable.setCategories(parseVariableCategories(dataVariableJson.getJsonArray("variableCategories"), dataVariable));
                
                // UNF
                dataVariable.setUnf(dataVariableJson.getString("UNF", null));
                
                // DataTable (do not forget this)
                dataVariable.setDataTable(dataTable);
                dataVariables.add(dataVariable);
                logger.log(Level.INFO, "dataVariables: current size={0}", dataVariables.size());
            }
        }
        logger.log(Level.INFO, "dataVariables:final size={0}", dataVariables.size());
        return dataVariables;
    }
    
    
    public List<SummaryStatistic> parseSummaryStatistics(JsonObject summaryStatisticsJson, DataVariable dataVariable){
        logger.log(Level.INFO, "parseSummaryStatistics is called");
        List<SummaryStatistic> summaryStatistics = new LinkedList<>();
        if (summaryStatisticsJson !=null){
            // mean
            String meanjsn = summaryStatisticsJson.getString("mean", null);
            if (StringUtils.isNotBlank(meanjsn)){
                SummaryStatistic mean = new SummaryStatistic();
                mean.setType(SummaryStatistic.SummaryStatisticType.MEAN);
                mean.setValue(meanjsn);
                mean.setDataVariable(dataVariable);
                summaryStatistics.add(mean);
            }
            // medn
            String mednjsn = summaryStatisticsJson.getString("medn", null);
            if (StringUtils.isNotBlank(mednjsn)){
                SummaryStatistic medn = new SummaryStatistic();
                medn.setType(SummaryStatistic.SummaryStatisticType.MEDN);
                medn.setValue(mednjsn);
                medn.setDataVariable(dataVariable);
                summaryStatistics.add(medn);
            }
            // mode
            String modejsn = summaryStatisticsJson.getString("mode", null);
            if (StringUtils.isNotBlank(modejsn)){
                SummaryStatistic mode = new SummaryStatistic();
                mode.setType(SummaryStatistic.SummaryStatisticType.MODE);
                mode.setValue(modejsn);
                mode.setDataVariable(dataVariable);
                summaryStatistics.add(mode);
            }
            // vald
            String valdjsn = summaryStatisticsJson.getString("vald", null);
            if (StringUtils.isNotBlank(valdjsn)){
                SummaryStatistic vald = new SummaryStatistic();
                vald.setType(SummaryStatistic.SummaryStatisticType.VALD);
                vald.setValue(valdjsn);
                vald.setDataVariable(dataVariable);
                summaryStatistics.add(vald);
            }
            // invd
            String invdjsn = summaryStatisticsJson.getString("invd", null);
            if (StringUtils.isNotBlank(invdjsn)){
                SummaryStatistic invd = new SummaryStatistic();
                invd.setType(SummaryStatistic.SummaryStatisticType.INVD);
                invd.setValue(invdjsn);
                invd.setDataVariable(dataVariable);
                summaryStatistics.add(invd);
            }
            // min
            String minjsn = summaryStatisticsJson.getString("min", null);
            if (StringUtils.isNotBlank(minjsn)){
                SummaryStatistic min = new SummaryStatistic();
                min.setType(SummaryStatistic.SummaryStatisticType.MIN);
                min.setValue(minjsn);
                min.setDataVariable(dataVariable);
                summaryStatistics.add(min);
            }
            // max
            String maxjsn = summaryStatisticsJson.getString("max", null);
            if (StringUtils.isNotBlank(maxjsn)){
                SummaryStatistic max = new SummaryStatistic();
                max.setType(SummaryStatistic.SummaryStatisticType.MAX);
                max.setValue(maxjsn);
                max.setDataVariable(dataVariable);
                summaryStatistics.add(max);
            }
            // stdev
            String stdevjsn = summaryStatisticsJson.getString("stdev", null);
            if (StringUtils.isNotBlank(stdevjsn)){
                SummaryStatistic stdev = new SummaryStatistic();
                stdev.setType(SummaryStatistic.SummaryStatisticType.STDEV);
                stdev.setValue(stdevjsn);
                stdev.setDataVariable(dataVariable);
                summaryStatistics.add(stdev);
            }
        }
        return summaryStatistics;
    }
    
    
    public List<VariableCategory> parseVariableCategories(JsonArray variableCategoriesJson, DataVariable dataVariable){
        logger.log(Level.INFO, "parseVariableCategories is called");
         List<VariableCategory> variableCategories = new LinkedList<>();
         if ((variableCategoriesJson != null) && (!variableCategoriesJson.isEmpty())){
             for (JsonObject variableCategoryJson : variableCategoriesJson.getValuesAs(JsonObject.class)){
                 VariableCategory vc = new VariableCategory();
                // label
                String label = variableCategoryJson.getString("label", "");
                vc.setLabel(label);
                // value
                String value = variableCategoryJson.getString("value", "");
                vc.setValue(value);
                vc.setDataVariable(dataVariable);
                variableCategories.add(vc);
             }
         }
        return variableCategories;
    }
    
    
    
    /**
     * Special processing for GeographicCoverage compound field:
     * Handle parsing exceptions caused by invalid controlled vocabulary in the "country" field by
     * putting the invalid data in "otherGeographicCoverage" in a new compound value.
     * 
     * @param ex - contains the invalid values to be processed
     * @return a compound DatasetField that contains the newly created values, in addition to 
     * the original valid values.
     * @throws JsonParseException 
     */
    private DatasetField remapGeographicCoverage(CompoundVocabularyException ex) throws JsonParseException{
        List<HashSet<FieldDTO>> geoCoverageList = new ArrayList<>();
        // For each exception, create HashSet of otherGeographic Coverage and add to list
        for (ControlledVocabularyException vocabEx : ex.getExList()) {
            HashSet<FieldDTO> set = new HashSet<>();
            set.add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.otherGeographicCoverage, vocabEx.getStrValue()));
            geoCoverageList.add(set);
        }
        FieldDTO geoCoverageDTO = FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.geographicCoverage, geoCoverageList);

        // convert DTO to datasetField so we can back valid values.
        Gson gson = new Gson();
        String jsonString = gson.toJson(geoCoverageDTO);
        JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
        JsonObject obj = jsonReader.readObject();
        DatasetField geoCoverageField = parseField(obj);

        // add back valid values
        for (DatasetFieldCompoundValue dsfcv : ex.getValidValues()) {
            if (!dsfcv.getChildDatasetFields().isEmpty()) {
                dsfcv.setParentDatasetField(geoCoverageField);
                geoCoverageField.getDatasetFieldCompoundValues().add(dsfcv);
            }
        }
        return geoCoverageField;
    }
    
    
    public DatasetField parseFieldForDelete(JsonObject json) throws JsonParseException{
        DatasetField ret = new DatasetField();
        DatasetFieldType type = datasetFieldSvc.findByNameOpt(json.getString("typeName", ""));   
        if (type == null) {
            throw new JsonParseException("Can't find type '" + json.getString("typeName", "") + "'");
        }
        return ret;
    }
     
    
    public DatasetField parseField(JsonObject json) throws JsonParseException{
        return parseField(json, true);
    }
    
    
    public DatasetField parseField(JsonObject json, Boolean testType) throws JsonParseException {
        logger.log(Level.INFO, "parseField: testType={0}", testType);
        if (json == null) {
            return null;
        }

        DatasetField ret = new DatasetField();
        DatasetFieldType type = datasetFieldSvc.findByNameOpt(json.getString("typeName", ""));


        if (type == null) {
            logger.fine("Can't find type '" + json.getString("typeName", "") + "'");
            return null;
        }
        logger.log(Level.INFO, "DatasetFieldType:name={0}", type.getName());
        logger.log(Level.INFO, "testType={0}", testType);
        logger.log(Level.INFO, "type.isAllowMultiples()={0}", type.isAllowMultiples());
        //logger.log(Level.INFO, "json={0}", xstream.toXML(json));
        
        
        if (testType && type.isAllowMultiples() != json.getBoolean("multiple")) {
            throw new JsonParseException("incorrect multiple   for field " + json.getString("typeName", ""));
        }
        if (testType && type.isCompound() && !json.getString("typeClass").equals("compound")) {
            throw new JsonParseException("incorrect  typeClass for field " + json.getString("typeName", "") + ", should be compound.");
        }
        if (testType && !type.isControlledVocabulary() && type.isPrimitive() && !json.getString("typeClass").equals("primitive")) {
            throw new JsonParseException("incorrect  typeClass for field: " + json.getString("typeName", "") + ", should be primitive");
        }
        if (testType && type.isControlledVocabulary() && !json.getString("typeClass").equals("controlledVocabulary")) {
            throw new JsonParseException("incorrect  typeClass for field " + json.getString("typeName", "") + ", should be controlledVocabulary");
        }
       
        
        ret.setDatasetFieldType(type);
               
        if (type.isCompound()) {
            List<DatasetFieldCompoundValue> vals = parseCompoundValue(type, json, testType);
            for (DatasetFieldCompoundValue dsfcv : vals) {
                dsfcv.setParentDatasetField(ret);
            }
            ret.setDatasetFieldCompoundValues(vals);

        } else if (type.isControlledVocabulary()) {
            List<ControlledVocabularyValue> vals = parseControlledVocabularyValue(type, json);
            for (ControlledVocabularyValue cvv : vals) {
                cvv.setDatasetFieldType(type);
            }
            ret.setControlledVocabularyValues(vals);

        } else {
            // primitive
            
                List<DatasetFieldValue> values = parsePrimitiveValue(type, json);
                for (DatasetFieldValue val : values) {
                    val.setDatasetField(ret);
                }
                ret.setDatasetFieldValues(values);
            }

        return ret;
    }
    
     public List<DatasetFieldCompoundValue> parseCompoundValue(DatasetFieldType compoundType, JsonObject json) throws JsonParseException {
         return parseCompoundValue(compoundType, json, true);
     }
    
    public List<DatasetFieldCompoundValue> parseCompoundValue(DatasetFieldType compoundType, JsonObject json, Boolean testType) throws JsonParseException {
        List<ControlledVocabularyException> vocabExceptions = new ArrayList<>();
        List<DatasetFieldCompoundValue> vals = new LinkedList<>();
        if (compoundType.isAllowMultiples()) {
            int order = 0;
            try {
                json.getJsonArray("value").getValuesAs(JsonObject.class);
            } catch (ClassCastException cce) {
                throw new JsonParseException("Invalid values submitted for " + compoundType.getName() + ". It should be an array of values.");
            }
            for (JsonObject obj : json.getJsonArray("value").getValuesAs(JsonObject.class)) {
                DatasetFieldCompoundValue cv = new DatasetFieldCompoundValue();
                List<DatasetField> fields = new LinkedList<>();
                for (String fieldName : obj.keySet()) {
                    JsonObject childFieldJson = obj.getJsonObject(fieldName);
                    DatasetField f=null;
                    try {
                        f = parseField(childFieldJson, testType);
                    } catch(ControlledVocabularyException ex) {
                        vocabExceptions.add(ex);
                    }
                    
                    if (f!=null) {
                        if (!compoundType.getChildDatasetFieldTypes().contains(f.getDatasetFieldType())) {
                            throw new JsonParseException("field " + f.getDatasetFieldType().getName() + " is not a child of " + compoundType.getName());
                        }
                        f.setParentDatasetFieldCompoundValue(cv);
                            fields.add(f);
                    }
                }
                if (!fields.isEmpty()) {
                    cv.setChildDatasetFields(fields);
                    cv.setDisplayOrder(order);
                    vals.add(cv);
                }
                order++;
            }

           

        } else {
            
            DatasetFieldCompoundValue cv = new DatasetFieldCompoundValue();
            List<DatasetField> fields = new LinkedList<>();
            JsonObject value = json.getJsonObject("value");
            for (String key : value.keySet()) {
                JsonObject childFieldJson = value.getJsonObject(key);
                DatasetField f = null;
                try {
                    f=parseField(childFieldJson, testType);
                } catch(ControlledVocabularyException ex ) {
                    vocabExceptions.add(ex);
                }
                if (f!=null) {
                    f.setParentDatasetFieldCompoundValue(cv);
                    fields.add(f);
                }
            }
            if (!fields.isEmpty()) {
                cv.setChildDatasetFields(fields);
                vals.add(cv);
            }
      
    }
        if (!vocabExceptions.isEmpty()) {
            throw new CompoundVocabularyException( "Invalid controlled vocabulary in compound field ", vocabExceptions, vals);
        }
          return vals;
    }

    public List<DatasetFieldValue> parsePrimitiveValue(DatasetFieldType dft , JsonObject json) throws JsonParseException {

        Map<Long, JsonObject> cvocMap = datasetFieldSvc.getCVocConf(true);
        boolean extVocab=false;
        if(cvocMap.containsKey(dft.getId())) {
            extVocab=true;
        }

        
        List<DatasetFieldValue> vals = new LinkedList<>();
        if (dft.isAllowMultiples()) {
           try {
            json.getJsonArray("value").getValuesAs(JsonObject.class);
            } catch (ClassCastException cce) {
                throw new JsonParseException("Invalid values submitted for " + dft.getName() + ". It should be an array of values.");
            }
            for (JsonString val : json.getJsonArray("value").getValuesAs(JsonString.class)) {
                DatasetFieldValue datasetFieldValue = new DatasetFieldValue();
                datasetFieldValue.setDisplayOrder(vals.size() - 1);
                datasetFieldValue.setValue(val.getString().trim());
                if(extVocab) {
                    if(!datasetFieldSvc.isValidCVocValue(dft, datasetFieldValue.getValue())) {
                        throw new JsonParseException("Invalid values submitted for " + dft.getName() + " which is limited to specific vocabularies.");
                    }
                    datasetFieldSvc.registerExternalTerm(cvocMap.get(dft.getId()), datasetFieldValue.getValue());
                }
                vals.add(datasetFieldValue);
            }

        } else {
            try {json.getString("value");}
            catch (ClassCastException cce) {
                throw new JsonParseException("Invalid value submitted for " + dft.getName() + ". It should be a single value.");
            }            
            DatasetFieldValue datasetFieldValue = new DatasetFieldValue();
            datasetFieldValue.setValue(json.getString("value", "").trim());
            if(extVocab) {
                if(!datasetFieldSvc.isValidCVocValue(dft, datasetFieldValue.getValue())) {
                    throw new JsonParseException("Invalid values submitted for " + dft.getName() + " which is limited to specific vocabularies.");
                }
                datasetFieldSvc.registerExternalTerm(cvocMap.get(dft.getId()), datasetFieldValue.getValue());
            }
            vals.add(datasetFieldValue);
        }

        return vals;
    }
    
    public Workflow parseWorkflow(JsonObject json) throws JsonParseException {
        Workflow retVal = new Workflow();
        validate("", json, "name", ValueType.STRING);
        validate("", json, "steps", ValueType.ARRAY);
        retVal.setName( json.getString("name") );
        JsonArray stepArray = json.getJsonArray("steps");
        List<WorkflowStepData> steps = new ArrayList<>(stepArray.size());
        for ( JsonValue jv : stepArray ) {
            steps.add(parseStepData((JsonObject) jv));
        }
        retVal.setSteps(steps);
        return retVal;
    }
    
    public WorkflowStepData parseStepData( JsonObject json ) throws JsonParseException {
        WorkflowStepData wsd = new WorkflowStepData();
        validate("step", json, "provider", ValueType.STRING);
        validate("step", json, "stepType", ValueType.STRING);
        
        wsd.setProviderId(json.getString("provider"));
        wsd.setStepType(json.getString("stepType"));
        if ( json.containsKey("parameters") ) {
            JsonObject params = json.getJsonObject("parameters");
            Map<String,String> paramMap = new HashMap<>();
            params.keySet().forEach(k -> paramMap.put(k,jsonValueToString(params.get(k))));
            wsd.setStepParameters(paramMap);
        }
        if ( json.containsKey("requiredSettings") ) {
            JsonObject settings = json.getJsonObject("requiredSettings");
            Map<String,String> settingsMap = new HashMap<>();
            settings.keySet().forEach(k -> settingsMap.put(k,jsonValueToString(settings.get(k))));
            wsd.setStepSettings(settingsMap);
        }
        return wsd;
    }
    
    private String jsonValueToString(JsonValue jv) {
        switch ( jv.getValueType() ) {
            case STRING: return ((JsonString)jv).getString();
            default: return jv.toString();
        }
    }
    
    public List<ControlledVocabularyValue> parseControlledVocabularyValue(DatasetFieldType cvvType, JsonObject json) throws JsonParseException {
        try {
            if (cvvType.isAllowMultiples()) {
                try {
                    json.getJsonArray("value").getValuesAs(JsonObject.class);
                } catch (ClassCastException cce) {
                    throw new JsonParseException("Invalid values submitted for " + cvvType.getName() + ". It should be an array of values.");
                }                
                List<ControlledVocabularyValue> vals = new LinkedList<>();
                for (JsonString strVal : json.getJsonArray("value").getValuesAs(JsonString.class)) {
                    String strValue = strVal.getString();
                    ControlledVocabularyValue cvv = datasetFieldSvc.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(cvvType, strValue, lenient);
                    if (cvv == null) {
                        throw new ControlledVocabularyException("Value '" + strValue + "' does not exist in type '" + cvvType.getName() + "'", cvvType, strValue);
                    }
                    // Only add value to the list if it is not a duplicate 
                    if (strValue.equals("Other")) {
                        System.out.println("vals = " + vals + ", contains: " + vals.contains(cvv));
                    }
                    if (!vals.contains(cvv)) {
                        vals.add(cvv);
                    }
                }
                return vals;

            } else {
                try {
                    json.getString("value");
                } catch (ClassCastException cce) {
                    throw new JsonParseException("Invalid value submitted for " + cvvType.getName() + ". It should be a single value.");
                }
                String strValue = json.getString("value", "");
                ControlledVocabularyValue cvv = datasetFieldSvc.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(cvvType, strValue, lenient);
                if (cvv == null) {
                    throw new ControlledVocabularyException("Value '" + strValue + "' does not exist in type '" + cvvType.getName() + "'", cvvType, strValue);
                }
                return Collections.singletonList(cvv);
            }
        } catch (ClassCastException cce) {
            throw new JsonParseException("Invalid values submitted for " + cvvType.getName());
        }
    }

    Date parseDate(String str) throws ParseException {
        return str == null ? null : Util.getDateFormat().parse(str);
    }

    Date parseTime(String str) throws ParseException {
        return str == null ? null : Util.getDateTimeFormat().parse(str);
    }

    Long parseLong(String str) throws NumberFormatException {
        return (str == null) ? null : Long.valueOf(str);
    }

    int parsePrimitiveInt(String str, int defaultValue) {
        return str == null ? defaultValue : Integer.parseInt(str);
    }
    
    public String parseHarvestingClient(JsonObject obj, HarvestingClient harvestingClient) throws JsonParseException {
        
        String dataverseAlias = obj.getString("dataverseAlias",null);
        
        harvestingClient.setName(obj.getString("nickName",null));
        harvestingClient.setHarvestType(obj.getString("type",null));
        harvestingClient.setHarvestingUrl(obj.getString("harvestUrl",null));
        harvestingClient.setArchiveUrl(obj.getString("archiveUrl",null));
        harvestingClient.setArchiveDescription(obj.getString("archiveDescription"));
        harvestingClient.setMetadataPrefix(obj.getString("metadataFormat",null));
        harvestingClient.setHarvestingSet(obj.getString("set",null));

        return dataverseAlias;
    }

    private List<DataFileCategory> getCategories(JsonObject filemetadataJson, Dataset dataset) {
        JsonArray categories = filemetadataJson.getJsonArray(OptionalFileParams.CATEGORIES_ATTR_NAME);
        if (categories == null || categories.isEmpty() || dataset == null) {
            return null;
        }
        List<DataFileCategory> dataFileCategories = new ArrayList<>();
        for (Object category : categories.getValuesAs(JsonString.class)) {
            JsonString categoryAsJsonString;
            try {
                categoryAsJsonString = (JsonString) category;
            } catch (ClassCastException ex) {
                logger.info("ClassCastException caught in getCategories: " + ex);
                return null;
            }
            DataFileCategory dfc = new DataFileCategory();
            dfc.setDataset(dataset);
            dfc.setName(categoryAsJsonString.getString());
            dataFileCategories.add(dfc);
        }
        return dataFileCategories;
    }
    
    /**
     * Validate than a JSON object has a field of an expected type, or throw an
     * inforamtive exception.
     * @param objectName
     * @param jobject
     * @param fieldName
     * @param expectedValueType
     * @throws JsonParseException 
     */
    private void validate(String objectName, JsonObject jobject, String fieldName, ValueType expectedValueType) throws JsonParseException {
        if ( (!jobject.containsKey(fieldName)) 
              || (jobject.get(fieldName).getValueType()!=expectedValueType) ) {
            throw new JsonParseException( objectName + " missing a field named '"+fieldName+"' of type " + expectedValueType );
        }
    }
}
