# This is a test to create an account. 
import unittest, time
from selenium import webdriver
from selenium.webdriver.common.by import By


class CreateAccountSuite(unittest.TestCase):

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
        driver=self.driver
        driver.get('http://dvn-build.hmdc.harvard.edu/dataverseuser.xhtml')
        driver.find_element(By.ID, "dataverseUserForm:editAccountButton_button").click()
        driver.find_element(By.LINK_TEXT, "Create Account").click()
        driver.find_element(By.ID, "dataverseUserForm:userName").click()
        driver.find_element(By.ID, "dataverseUserForm:userName").clear()
        driver.find_element(By.ID, "dataverseUserForm:userName").send_keys("user1")
        driver.find_element(By.ID, "dataverseUserForm:inputPassword").click()
        driver.find_element(By.ID, "dataverseUserForm:inputPassword").clear()
        driver.find_element(By.ID, "dataverseUserForm:inputPassword").send_keys("u")
        driver.find_element(By.ID, "dataverseUserForm:retypePassword").click()
        driver.find_element(By.ID, "dataverseUserForm:retypePassword").clear()
        driver.find_element(By.ID, "dataverseUserForm:retypePassword").send_keys("u")
        driver.find_element(By.ID, "dataverseUserForm:firstName").click()
        driver.find_element(By.ID, "dataverseUserForm:firstName").clear()
        driver.find_element(By.ID, "dataverseUserForm:firstName").send_keys("user")
        driver.find_element(By.ID, "dataverseUserForm:lastName").click()
        driver.find_element(By.ID, "dataverseUserForm:lastName").clear()
        driver.find_element(By.ID, "dataverseUserForm:lastName").send_keys("zero")
        driver.find_element(By.ID, "dataverseUserForm:email").click()
        driver.find_element(By.ID, "dataverseUserForm:email").clear()
        driver.find_element(By.ID, "dataverseUserForm:email").send_keys("u@u.edu")
        driver.find_element(By.ID, "dataverseUserForm:institution").click()
        driver.find_element(By.ID, "dataverseUserForm:institution").clear()
        driver.find_element(By.ID, "dataverseUserForm:institution").send_keys("IQSS")
        driver.find_element(By.ID, "dataverseUserForm:j_idt45_focus").click()
        driver.find_element(By.ID, "dataverseUserForm:j_idt45_focus").send_keys("\\9")
        driver.find_element(By.CSS_SELECTOR, "span.ui-icon.ui-icon-triangle-1-s").click()
        driver.find_element(By.XPATH, "//div[@class='ui-selectonemenu-items-wrapper']//li[.='Student']").click()
        driver.find_element(By.ID, "dataverseUserForm:phone").click()
        driver.find_element(By.ID, "dataverseUserForm:phone").click()
        driver.find_element(By.ID, "dataverseUserForm:phone").clear()
        driver.find_element(By.ID, "dataverseUserForm:phone").send_keys("888-888-8888")
        driver.find_element(By.ID, "dataverseUserForm:save").click()


    def tearDown(self):
        print("Link to your job: https://saucelabs.com/jobs/%s" % self.driver.session_id)
        self.driver.quit()

if __name__ == '__main__':
    unittest.main()