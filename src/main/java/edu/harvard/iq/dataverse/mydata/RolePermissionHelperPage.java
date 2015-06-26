package edu.harvard.iq.dataverse.mydata;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import edu.harvard.iq.dataverse.DatasetPage;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.authorization.DataverseRole;
import edu.harvard.iq.dataverse.authorization.DataverseRolePermissionHelper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author rmp553
 */
@ViewScoped
@Named("RolePermissionHelperPage")
public class RolePermissionHelperPage implements java.io.Serializable {
    
    private static final Logger logger = Logger.getLogger(DatasetPage.class.getCanonicalName());

    @Inject DataverseSession session;    

    @EJB
    DataverseRoleServiceBean roleService;
    
    private String testName = "blah";
    private DataverseRolePermissionHelper rolePermissionHelper;// = new DataverseRolePermissionHelper();
    
     
    private void msg(String s){
        System.out.println(s);
    }
    
    private void msgt(String s){
        msg("-------------------------------");
        msg(s);
        msg("-------------------------------");
    }
    
    public String init() {
        msgt("----------- init() -------------");
        List<DataverseRole> roleList = roleService.findAll();
        msgt("roles: " + roleList.toString());
        rolePermissionHelper = new DataverseRolePermissionHelper(roleList);
        return null;
    }
    
    public DataverseRolePermissionHelper getRolePermissionHelper(){
        return this.rolePermissionHelper;
    }
    
    public String getRoleInfo() throws IOException{
         TemplateLoader loader = new FileTemplateLoader("/Users/rmp553/NetBeansProjects/dataverse/src/main/java/edu/harvard/iq/dataverse/mydata/",
  ".hbs");
        //loader.setPrefix("resources/");
        //loader.setSuffix(".html");
        Handlebars handlebars = new Handlebars(loader);
        
        Template template = handlebars.compile("role_table");

        
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "--name here--");
        //dict.put("pager", pager);
        String pageString = template.apply(dict);
        
        return pageString;
    }
    
    public String getTestName(){
        return this.testName;//"blah";
    }

    public void setTestName(String name){
        this.testName = name;
    }

    public String getSomeText(){
        //System.out.println(this.rolePermissionHelper.getRoleNameListString());;
        return "pigletz";
        //return this.rolePermissionHelper.getRoleNameListString();
    }
}
