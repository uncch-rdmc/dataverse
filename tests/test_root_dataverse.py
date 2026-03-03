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

class test_root_dataverse(unittest.TestCase):
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
    
    def test_test_root_dataverse(self):
        success = True
        wd = self.wd
        try:
            wd.get(config.accessURL)
            time.sleep(2)  # Wait for page to load
            
            # Check if root dataverse already exists
            try:
                html_element = wd.find_element(By.TAG_NAME, "html")
                page_text = html_element.text
                if not ("Create Root Dataverse" in page_text):
                    print("Root dataverse exists. Exiting.")
                    return  # Exit early if root dataverse already exists
            except NoSuchElementException:
                success = False
                print("Could not find HTML element to check for root dataverse.")
                return
            
            # Fill out root dataverse form
            root_dv_fields = [
                ("dataverseForm:name", "root dv"),
                ("dataverseForm:alias", "rootdv"),
                ("dataverseForm:contactEmail", "kcondon@hmdc.harvard.edu"),
                ("dataverseForm:affiliation", "IQSS"),
                ("dataverseForm:description", "This is a test")
            ]
            
            for field_id, value in root_dv_fields:
                try:
                    field = wd.find_element(By.ID, field_id)
                    field.click()
                    field.clear()
                    field.send_keys(value)
                except NoSuchElementException:
                    success = False
                    print(f"Could not find root dataverse form field: {field_id}")
            
            # Try to save root dataverse
            try:
                save_button = wd.find_element(By.ID, "dataverseForm:save")
                save_button.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find root dataverse save button.")
            
            # Verify root dataverse was created
            try:
                html_element = wd.find_element(By.TAG_NAME, "html")
                page_text = html_element.text
                if not ("root dv" in page_text):
                    success = False
                    print("verify root dv name failed")
            except NoSuchElementException:
                success = False
                print("Could not find HTML element to verify root dataverse creation.")
                
        except Exception as e:
            success = False
            print(f"Error in test_test_root_dataverse: {e}")
            
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
