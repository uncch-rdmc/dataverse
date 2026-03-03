from selenium import webdriver
from selenium.webdriver.common.by import By
import time, unittest, config

def is_alert_present(wd):
    try:
        wd.switch_to.alert.text
        return True
    except:
        return False

class test_create_test_account(unittest.TestCase):
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
    
    def test_test_create_test_account(self):
        success = True
        wd = self.wd
        wd.get(config.accessURL)
        wd.find_element(By.LINK_TEXT, "Create Account").click()
        wd.find_element(By.ID, "dataverseUserForm:userName").click()
        wd.find_element(By.ID, "dataverseUserForm:userName").clear()
        wd.find_element(By.ID, "dataverseUserForm:userName").send_keys("tester")
        wd.find_element(By.ID, "dataverseUserForm:inputPassword").click()
        wd.find_element(By.ID, "dataverseUserForm:inputPassword").clear()
        wd.find_element(By.ID, "dataverseUserForm:inputPassword").send_keys("tester")
        wd.find_element(By.ID, "dataverseUserForm:retypePassword").click()
        wd.find_element(By.ID, "dataverseUserForm:retypePassword").clear()
        wd.find_element(By.ID, "dataverseUserForm:retypePassword").send_keys("tester")
        wd.find_element(By.ID, "dataverseUserForm:firstName").click()
        wd.find_element(By.ID, "dataverseUserForm:firstName").clear()
        wd.find_element(By.ID, "dataverseUserForm:firstName").send_keys("test")
        wd.find_element(By.ID, "dataverseUserForm:lastName").click()
        wd.find_element(By.ID, "dataverseUserForm:lastName").clear()
        wd.find_element(By.ID, "dataverseUserForm:lastName").send_keys("user")
        wd.find_element(By.ID, "dataverseUserForm:email").click()
        wd.find_element(By.ID, "dataverseUserForm:email").clear()
        wd.find_element(By.ID, "dataverseUserForm:email").send_keys("kcondon@hmdc.harvard.edu")
        wd.find_element(By.ID, "dataverseUserForm:institution").click()
        wd.find_element(By.ID, "dataverseUserForm:institution").clear()
        wd.find_element(By.ID, "dataverseUserForm:institution").send_keys("IQSS")
        wd.find_element(By.XPATH, "//div[@id='dataverseUserForm:j_idt45']/div[3]").click()
        wd.find_element(By.XPATH, "//div[@class='ui-selectonemenu-items-wrapper']//li[.='Staff']").click()
        wd.find_element(By.ID, "dataverseUserForm:phone").click()
        wd.find_element(By.ID, "dataverseUserForm:phone").clear()
        wd.find_element(By.ID, "dataverseUserForm:phone").send_keys("1-222-333-4444")
        wd.find_element(By.ID, "dataverseUserForm:save").click()
        time.sleep(1)
        if ("This Username is already taken." in wd.find_element(By.TAG_NAME, "html").text):
            print("Username exists. Exiting.")
            return   
        if not ("Log Out" in wd.find_element(By.TAG_NAME, "html").text): 
            success = False
            print("User was not logged in after create account.")           
        self.assertTrue(success)
    
    def tearDown(self):
        if not (config.local):
            print("Link to your job: https://saucelabs.com/jobs/%s" % self.wd.session_id)        
        self.wd.quit()

if __name__ == '__main__':
    unittest.main()