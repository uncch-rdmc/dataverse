# This contain a list of individual test and will be run from Jenkins.

import unittest
import test_access
import test_create_test_account
import test_root_dataverse
import test_account
import test_dataverse
import test_dataset
import test_dataset_fileupload

# This is a list of testFileName.testClass
def suite():
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    
    # Add test cases using the modern TestLoader approach
    suite.addTests(loader.loadTestsFromTestCase(test_access.test_access))
    suite.addTests(loader.loadTestsFromTestCase(test_create_test_account.test_create_test_account))
    suite.addTests(loader.loadTestsFromTestCase(test_root_dataverse.test_root_dataverse))
    suite.addTests(loader.loadTestsFromTestCase(test_account.test_account))
    suite.addTests(loader.loadTestsFromTestCase(test_dataverse.test_dataverse))
    suite.addTests(loader.loadTestsFromTestCase(test_dataset.test_dataset))
    suite.addTests(loader.loadTestsFromTestCase(test_dataset_fileupload.test_dataset_fileupload))
    
    return suite

if __name__ == '__main__':
    result = unittest.TextTestRunner(verbosity=2).run(suite())
