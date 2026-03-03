from selenium import webdriver
from selenium.webdriver.common.by import By
import time, unittest, config

def is_alert_present(wd):
    try:
        wd.switch_to.alert.text
        return True
    except:
        return False

class test_dataverse(unittest.TestCase):
    def setUp(self):
        from selenium.webdriver.chrome.options import Options

        options = Options()
        options.add_argument("--headless=new")
        options.add_argument("--no-sandbox")
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument("--window-size=1920,1080")

        if (config.local):
            self.wd = webdriver.Chrome(options=options)
        else:
            self.wd = webdriver.Remote(
                options=options,
                command_executor="http://esodvn:325caef9-81dd-47a5-8b74-433057ce888f@ondemand.saucelabs.com:80/wd/hub"
            )
 
        self.wd.implicitly_wait(60)
    
    def test_test_dataverse(self):
        success = True
        wd = self.wd
        wd.get(config.accessURL)
        wd.find_element(By.LINK_TEXT, "Log In").click()
        time.sleep(1)
        if not ("Login" in wd.find_element(By.TAG_NAME, "html").text):
            success = False
            print("verifyTextPresent failed")
        wd.find_element(By.ID, "loginForm:userName").click()
        wd.find_element(By.ID, "loginForm:userName").clear()
        wd.find_element(By.ID, "loginForm:userName").send_keys("tester")
        wd.find_element(By.ID, "loginForm:password").click()
        wd.find_element(By.ID, "loginForm:password").clear()
        wd.find_element(By.ID, "loginForm:password").send_keys("tester")
        wd.find_element(By.ID, "loginForm:login").click()
        wd.find_element(By.ID, "shareForm:shareData_button").click()
        wd.find_element(By.LINK_TEXT, "Create Dataverse").click()
        wd.find_element(By.ID, "dataverseForm:name").click()
        wd.find_element(By.ID, "dataverseForm:name").clear()
        wd.find_element(By.ID, "dataverseForm:name").send_keys("test dv")
        wd.find_element(By.ID, "dataverseForm:alias").click()
        wd.find_element(By.ID, "dataverseForm:alias").clear()
        wd.find_element(By.ID, "dataverseForm:alias").send_keys("testdv")
        wd.find_element(By.ID, "dataverseForm:contactEmail").click()
        wd.find_element(By.ID, "dataverseForm:contactEmail").clear()
        wd.find_element(By.ID, "dataverseForm:contactEmail").send_keys("kcondon@hmdc.harvard.edu")
        wd.find_element(By.ID, "dataverseForm:affiliation").click()
        wd.find_element(By.ID, "dataverseForm:affiliation").clear()
        wd.find_element(By.ID, "dataverseForm:affiliation").send_keys("IQSS")
        wd.find_element(By.ID, "dataverseForm:description").click()
        wd.find_element(By.ID, "dataverseForm:description").clear()
        wd.find_element(By.ID, "dataverseForm:description").send_keys("This is a test")
        wd.find_element(By.ID, "dataverseForm:save").click()

        wd.find_element(By.LINK_TEXT, "Log Out").click()
        self.assertTrue(success)
    
    def tearDown(self):
        if not (config.local):
            print("Link to your job: https://saucelabs.com/jobs/%s" % self.wd.session_id)        
        self.wd.quit()

if __name__ == '__main__':
    unittest.main()