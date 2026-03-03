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

class test_access(unittest.TestCase):
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

    
    def test_test_access(self):
        success = True
        wd = self.wd
        try:
            wd.get(config.accessURL)
            time.sleep(2)  # Wait for page to load
            
            # Check if the page loaded successfully
            try:
                html_element = wd.find_element(By.TAG_NAME, "html")
                page_text = html_element.text
                if not ("Log In" in page_text):
                    success = False
                    print("Could not verify page text.") 
            except NoSuchElementException:
                success = False
                print("Could not find HTML element to check page text.")
                
        except Exception as e:
            success = False
            print(f"Error in test_test_access: {e}")
            
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
