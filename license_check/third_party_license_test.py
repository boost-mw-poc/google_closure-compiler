"""Check and generate the licenses for third party dependencies.

This script generates the THIRD_PARTY_NOTICES file, which is the licenses
for all our external dependencies. It reads the MODULE.bazel file,
compares the pom.xml and gradle files with the jar file list,
and then generates the license file.
"""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
import requests

# Constants
GITHUB_PREFIX = 'https://github.com/'
RAW_FILE_PREFIX = 'https://raw.githubusercontent.com/'
VERSION_TAG_SEPARATOR = '@/'

POM_FILE_SUFFIX = '.xml'
GRADLE_FILE_SUFFIX = '.gradle'

SPACER = """

===============================================================================
===============================================================================
===============================================================================

"""


def get_file_from_github(github_url):
  raw_file_url = (
      github_url
      .replace(GITHUB_PREFIX, RAW_FILE_PREFIX)
      .replace(VERSION_TAG_SEPARATOR, '/')
      .replace('/blob/', '/refs/tags/')
  )

  print('Requesting: ', raw_file_url)
  response = requests.get(raw_file_url)

  if response.status_code != 200:
    return None

  return response.text


def get_pom_artifact_name(pom_xml_github_url):
  pom_xml_content = get_file_from_github(pom_xml_github_url)
  if pom_xml_content is None:
    print('Github Returned Error status when reading :', pom_xml_github_url)
    sys.exit(1)

  root = ET.fromstring(pom_xml_content)
  ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}

  parent = root.find('mvn:parent', ns)
  group_id = parent.find('mvn:groupId', ns).text
  artifact_id = root.find('mvn:artifactId', ns).text

  return '%s:%s' % (group_id, artifact_id)


def get_grad_rval(line):
  return (line.split()[-1]).strip("'")


def get_gradle_artifact_name(url):
  content = get_file_from_github(url)

  group_id = ''
  artifact_id = ''

  for line in content.splitlines():
    if 'groupId' in line:
      group_id = get_grad_rval(line)
    if 'artifactId' in line:
      artifact_id = get_grad_rval(line)

  return '%s:%s' % (group_id, artifact_id)


def get_license_from_pom(url):
  github_branch_root = url.split(VERSION_TAG_SEPARATOR)[0]
  license_filenames = ['/LICENSE', '/COPYING']

  license_content = None

  for filename in license_filenames:
    license_url = github_branch_root + filename
    license_content = get_file_from_github(license_url)
    if license_content is not None:
      break

  if license_content is None:
    print('Cannot get license information for pom/gradle file: ', url)
    sys.exit(1)

  return license_content


def get_license_from_absolute_url(url):
  license_content = get_file_from_github(url)
  if license_content is None:
    print('Cannot get license information for GitHub url: ', url)
    sys.exit(1)
  return license_content


def main():
  parser = argparse.ArgumentParser(
      prog='ThirdPartyLicenseTest',
      description='Checks if the third party licenses are up to date',
      epilog='',
  )
  parser.add_argument(
      '-u',
      '--update',
      action='store_true',
      help='Update THIRD_PARTY_NOTICES with the new content.',
  )
  args = parser.parse_args()

  maven_artifacts_file = 'MODULE.bazel'
  third_party_notices_file = 'THIRD_PARTY_NOTICES'

  # Read artifacts from MODULE.bazel
  contents = open(maven_artifacts_file).read()
  pattern = r'START_MAVEN_ARTIFACTS_LIST\s*(.*?)\s*END_MAVEN_ARTIFACTS_LIST'
  bzl_file_contents = re.search(pattern, contents, re.DOTALL).group(1)

  # Work around a python3 bug with exec and local variables
  ldict = {}
  exec(bzl_file_contents, globals(), ldict)  # pylint: disable=exec-used
  maven_artifacts = ldict['MAVEN_ARTIFACTS']
  pom_gradle_filelist = ldict[
      'ORDERED_POM_OR_GRADLE_FILE_LIST_FOR_LICENSE_CHECK'
  ]
  additional_licenses = ldict['ADDITIONAL_LICENSES']

  # Compare list lengths
  if len(maven_artifacts) != len(pom_gradle_filelist) + len(
      additional_licenses
  ):
    print(
        'artifact list length and pom/gradle file list length is not equal. ',
        'Please check the file :',
        maven_artifacts_file,
    )
    sys.exit(1)

  package_name_to_pom = {}

  # Compare pom/gradle artifact names with maven jar files
  artifact_list_from_github = []
  for url in pom_gradle_filelist:
    if url.endswith(POM_FILE_SUFFIX):
      tmp = get_pom_artifact_name(url)
      artifact_list_from_github.append(tmp)
      package_name_to_pom[tmp] = url
    elif url.endswith(GRADLE_FILE_SUFFIX):
      tmp = get_gradle_artifact_name(url)
      artifact_list_from_github.append(tmp)
      package_name_to_pom[tmp] = url
    else:
      print('Neither a Pom, nor a Gradle a file found in the list. exiting.')
      sys.exit(1)

  artifact_list_from_maven = []
  for artifact_name in maven_artifacts:
    split_artifact_name = artifact_name.split(':')
    artifact_list_from_maven.append(
        '%s:%s' % (split_artifact_name[0], split_artifact_name[1])
    )

  gh_artifact_set = set(
      artifact_list_from_github + list(additional_licenses.keys())
  )
  mvn_artifact_set = set(artifact_list_from_maven)
  if gh_artifact_set != mvn_artifact_set:
    print('Artifact names from github and maven are different.')
    print('----------')
    print('Github artifact list only: ', gh_artifact_set - mvn_artifact_set)
    print('Maven artifact list only: ', mvn_artifact_set - gh_artifact_set)
    sys.exit(1)

  license_content_to_package = {}
  # Create a dictionary of license names to maven jar files
  for package, pom_url in package_name_to_pom.items():
    license_content = get_license_from_pom(pom_url)
    license_content_to_package.setdefault(license_content, []).append(package)

  for package, url in additional_licenses.items():
    license_content = get_license_from_absolute_url(url)
    license_content_to_package.setdefault(license_content, []).append(package)

  # Create THIRD_PARTY_NOTICES
  third_party_notices_content = ''
  for license_content in license_content_to_package:
    third_party_notices_content += 'License for package(s): ' + str(
        license_content_to_package[license_content]
    )
    third_party_notices_content += '\n\n'
    third_party_notices_content += license_content
    third_party_notices_content += SPACER

  # Compare or Write out THIRD_PARTY_NOTICES file
  if args.update:
    fh = open(third_party_notices_file, 'w')
    fh.write(third_party_notices_content)
    fh.close()
    sys.exit()

  else:
    old_third_party_notices_content = open(third_party_notices_file).read()
    if old_third_party_notices_content == third_party_notices_content:
      print('Success: THIRD_PARTY_NOTICES file is up-to-date.')
      sys.exit()
    else:
      print('Changes detected in THIRD_PARTY_NOTICES file!')
      print('Please run with --update flag to update the license file.')
      sys.exit(1)


if __name__ == '__main__':
  main()
