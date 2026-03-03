from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.common.exceptions import NoSuchElementException
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
        try:
            wd.get(config.accessURL)
            time.sleep(2)  # Wait for page to load
            
            # Try to find and click Log In link
            try:
                login_link = wd.find_element(By.LINK_TEXT, "Log In")
                login_link.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find 'Log In' link.")
                return  # Exit early if we can't proceed
            
            # Verify login page text
            try:
                html_element = wd.find_element(By.TAG_NAME, "html")
                page_text = html_element.text
                if not ("Login" in page_text):
                    success = False
                    print("verifyTextPresent failed")
            except NoSuchElementException:
                success = False
                print("Could not find HTML element to check login page text.")
            
            # Fill out login form
            login_fields = [
                ("loginForm:userName", "tester"),
                ("loginForm:password", "tester")
            ]
            
            for field_id, value in login_fields:
                try:
                    field = wd.find_element(By.ID, field_id)
                    field.click()
                    field.clear()
                    field.send_keys(value)
                except NoSuchElementException:
                    success = False
                    print(f"Could not find login field: {field_id}")
            
            # Try to submit login
            try:
                login_button = wd.find_element(By.ID, "loginForm:login")
                login_button.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find login button.")
            
            # Try to create dataverse
            try:
                share_button = wd.find_element(By.ID, "shareForm:shareData_button")
                share_button.click()
                time.sleep(1)
                
                create_dv_link = wd.find_element(By.LINK_TEXT, "Create Dataverse")
                create_dv_link.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find share button or Create Dataverse link.")
            
            # Fill out dataverse form
            dv_fields = [
                ("dataverseForm:name", "test dv"),
                ("dataverseForm:alias", "testdv"),
                ("dataverseForm:contactEmail", "kcondon@hmdc.harvard.edu"),
                ("dataverseForm:affiliation", "IQSS"),
                ("dataverseForm:description", "This is a test")
            ]
            
            for field_id, value in dv_fields:
                try:
                    field = wd.find_element(By.ID, field_id)
                    field.click()
                    field.clear()
                    field.send_keys(value)
                except NoSuchElementException:
                    success = False
                    print(f"Could not find dataverse form field: {field_id}")
            
            # Try to save dataverse
            try:
                save_button = wd.find_element(By.ID, "dataverseForm:save")
                save_button.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find dataverse save button.")
            
            # Try to logout
            try:
                logout_link = wd.find_element(By.LINK_TEXT, "Log Out")
                logout_link.click()
            except NoSuchElementException:
                success = False
                print("Could not find Log Out link.")
                
        except Exception as e:
            success = False
            print(f"Error in test_test_dataverse: {e}")
            
        self.assertTrue(success)
    
    def tearDown(self):
        try:
            if not (config.local):
                print("Link to your job: https://saucelabs.com/jobs/%s" % self.wd.session_id)        
            self.wd.quit()
        except:
            pass  # Ignore errors in tearDown

if __name__ == '__main__':
    unittest.main()
