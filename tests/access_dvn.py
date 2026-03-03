# This is a test to access Dataverse homepage. 
import unittest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.common.exceptions import NoSuchElementException


class AccessDVN(unittest.TestCase):

    def setUp(self):
        from selenium.webdriver.chrome.options import Options
        
        options = Options()
        options.add_argument("--headless=new")
        options.add_argument("--no-sandbox")
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument("--window-size=1920,1080")

        self.driver = webdriver.Remote(
            options=options,
            command_executor="http://esodvn:325caef9-81dd-47a5-8b74-433057ce888f@ondemand.saucelabs.com:80/wd/hub"
        )
        self.driver.implicitly_wait(30)

    def test_sauce(self):
        try:
            self.driver.get('http://dvn-build.hmdc.harvard.edu')
            time.sleep(2)  # Wait for page to load
        except Exception as e:
            print(f"Error in test_sauce: {e}")

    def tearDown(self):
        try:
            print("Link to your job: https://saucelabs.com/jobs/%s" % self.driver.session_id)
            self.driver.quit()
        except:
            pass  # Ignore errors in tearDown

if __name__ == '__main__':
    unittest.main()
