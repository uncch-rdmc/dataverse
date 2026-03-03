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
        try:
            wd.get(config.accessURL)
            time.sleep(2)  # Wait for page to load
            
            # Try to find and click Create Account link
            try:
                create_account_link = wd.find_element(By.LINK_TEXT, "Create Account")
                create_account_link.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find 'Create Account' link.")
                return  # Exit early if we can't proceed
            
            # Fill out the form fields with error handling
            form_fields = [
                ("dataverseUserForm:userName", "tester"),
                ("dataverseUserForm:inputPassword", "tester"),
                ("dataverseUserForm:retypePassword", "tester"),
                ("dataverseUserForm:firstName", "test"),
                ("dataverseUserForm:lastName", "user"),
                ("dataverseUserForm:email", "kcondon@hmdc.harvard.edu"),
                ("dataverseUserForm:institution", "IQSS"),
                ("dataverseUserForm:phone", "1-222-333-4444")
            ]
            
            for field_id, value in form_fields:
                try:
                    field = wd.find_element(By.ID, field_id)
                    field.click()
                    field.clear()
                    field.send_keys(value)
                except NoSuchElementException:
                    success = False
                    print(f"Could not find form field: {field_id}")
            
            # Handle dropdown selection
            try:
                dropdown_trigger = wd.find_element(By.XPATH, "//div[@id='dataverseUserForm:j_idt45']/div[3]")
                dropdown_trigger.click()
                time.sleep(0.5)
                
                staff_option = wd.find_element(By.XPATH, "//div[@class='ui-selectonemenu-items-wrapper']//li[.='Staff']")
                staff_option.click()
            except NoSuchElementException:
                success = False
                print("Could not find dropdown or Staff option.")
            
            # Try to submit the form
            try:
                save_button = wd.find_element(By.ID, "dataverseUserForm:save")
                save_button.click()
                time.sleep(1)
            except NoSuchElementException:
                success = False
                print("Could not find save button.")
            
            # Check for username already taken
            try:
                html_element = wd.find_element(By.TAG_NAME, "html")
                page_text = html_element.text
                if ("This Username is already taken." in page_text):
                    print("Username exists. Exiting.")
                    return   
            except NoSuchElementException:
                success = False
                print("Could not find HTML element to check for username conflict.")
            
            # Check if user was logged in
            try:
                html_element = wd.find_element(By.TAG_NAME, "html")
                page_text = html_element.text
                if not ("Log Out" in page_text): 
                    success = False
                    print("User was not logged in after create account.")
            except NoSuchElementException:
                success = False
                print("Could not find HTML element to check login status.")
                
        except Exception as e:
            success = False
            print(f"Error in test_test_create_test_account: {e}")
            
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
