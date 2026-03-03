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

class test_dataset_fileupload(unittest.TestCase):
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
    
    def test_test_dataset_fileupload(self):
        success = True
        wd = self.wd
        try:
            wd.get(config.accessURL)
            time.sleep(2)  # Wait for page to load
            
            # Log in as test user
            try:
                login_link = wd.find_element(By.LINK_TEXT, "Log In")
                login_link.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find 'Log In' link.")
                return  # Exit early if we can't proceed
            
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
            
            # Choose to upload dataset
            try:
                share_button = wd.find_element(By.ID, "shareForm:shareData_button")
                share_button.click()
                time.sleep(1)
                
                add_dataset_link = wd.find_element(By.LINK_TEXT, "Add Dataset")
                add_dataset_link.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find share button or Add Dataset link.")
            
            # Enter dataset info
            dataset_fields = [
                ("datasetForm:title", "Test Drag and Drop"),
                ("datasetForm:author", "tester"),
                ("datasetForm:date", "2013"),
                ("datasetForm:distributor", "test"),
                ("datasetForm:description", "This is a test.")
            ]
            
            for field_id, value in dataset_fields:
                try:
                    field = wd.find_element(By.ID, field_id)
                    field.click()
                    field.clear()
                    field.send_keys(value)
                except NoSuchElementException:
                    success = False
                    print(f"Could not find dataset form field: {field_id}")
            
            # Upload file (this might fail in headless mode or CI environment)
            try:
                file_upload = wd.find_element(By.ID, "datasetForm:tabView:fileUpload_input")
                file_upload.send_keys("/Users/kcondon/Downloads/50by1000.dta")
                time.sleep(3)
                
                # Check if file was uploaded
                try:
                    file_name_element = wd.find_element(By.ID, "datasetForm:tabView:filesTable:0:fileName")
                    file_name = file_name_element.get_attribute("value")
                    if file_name != "50by1000.dta":
                        success = False
                        print("Could not find file name in upload table.")
                except NoSuchElementException:
                    success = False
                    print("Could not find file name element in upload table.")
            except NoSuchElementException:
                success = False
                print("Could not find file upload input.")
            
            # Try to save dataset
            try:
                save_button = wd.find_element(By.ID, "datasetForm:save")
                save_button.click()
                time.sleep(3)
            except NoSuchElementException:
                success = False
                print("Could not find dataset save button.")
            
            # Verify results
            try:
                html_element = wd.find_element(By.TAG_NAME, "html")
                page_text = html_element.text
                
                if not ("Test Drag and Drop" in page_text):
                    success = False
                    print("Could not find dataset title on page.")
                
                if not ("tester, \"Test Drag and Drop\", 2013, test, http://dx.doi.org/10.1234/dataverse/" in page_text):
                    success = False
                    print("Could not verify data citation.")
            except NoSuchElementException:
                success = False
                print("Could not find HTML element to verify results.")
            
            # Try to logout
            try:
                logout_link = wd.find_element(By.LINK_TEXT, "Log Out")
                logout_link.click()
            except NoSuchElementException:
                success = False
                print("Could not find Log Out link.")
                
        except Exception as e:
            success = False
            print(f"Error in test_test_dataset_fileupload: {e}")
            
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
