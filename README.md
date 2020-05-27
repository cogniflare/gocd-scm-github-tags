
# GoCD - Git tags and releases support
This is a GoCD SCM plugin for Git tag support.

Supported (as separate plugins):
* Github repository for Releases

## Requirements
These plugins require GoCD version v20.x or above.

## Get Started
**Installation:**
- Download the latest plugin jar from the [Releases](https://github.com/ashwanthkumar/gocd-build-github-pull-requests/releases) section. Place it in `<go-server-location>/plugins/external` & restart Go Server. You can find the location of the Go Server installation [here](http://www.go.cd/documentation/user/current/installation/installing_go_server.html#location-of-files-after-installation-of-go-server).

**Usage: (Outdated, forked from another repo)**

* Make sure plugins are loaded. Note: You can use [GoCD build status notifier](https://github.com/srinivasupadhya/gocd-build-status-notifier) to update status of Pull Requests with build status.
![Plugins listing page][1]

* Assuming you already have a pipeline "ProjectA" for one of your repos
![Original pipeline][2]
![Original pipeline material listing page][3]

* 'Extract Template' from the pipeline, if its not templatized already (this is optional step) 
![Pipelines listing page][4]
![Extract template pop-up][5]

* Create new pipeline say "ProjectA-FeatureBranch" off of the extracted template. You can clone "ProjectA" pipeline to achieve this.
![Pipelines listing page after extract template][6]
![Clone pipeline pop-up][7]

* In the materials configuration for your newly created pipeline, you will see that there is a new material for each of the plugins you have installed (Git Feature Branch, Github, Stash or Gerrit). Select one of these new materials, fill in the details and the plugin will build the pull requests from the given material.
![Select GitHub drop-down][8]
![Add GitHub drop-down][9]
![New pipeline material listing page][10]

* You can delete the old material that is left over from cloning your pipeline.
![Delete old material pop-up][11]

## Behavior
- First run of the new pipeline will be off of the 'Default Branch' configured in the Material. This creates base PR-Revision map. It also serves as sanity check for newly created pipeline.

- From then on, any new change (new PR create / new commits to existing PR) will trigger the new pipeline.
![New pipeine schedule][12]

- PR details (id, author etc.) will be available as environment variables for tasks to consume.

- Build status notifier plugin will update Pull Request with build status
![On successful run of new pipeline][13]

### Github

**Authentication:**
- You can create a file `~/.github` with the following contents: (Note: `~/.github` needs to be available on Go Server)
```
login=johndoe
password=thisaintapassword
```

- You can also generate & use oauth token. To do so create a file `~/.github` with the following contents: (Note: `~/.github` needs to be available on Go Server)
```
login=johndoe
oauth=thisaintatoken
```

**Github Enterprise:**
- If you intend to use this plugin with 'Github Enterprise' then add the following content in `~/.github` (Note: `~/.github` needs to be available on Go Server)
```
# for enterprise installations - Make sure to add /api/v3 to the hostname
# ignore this field or have the value to https://api.github.com
endpoint=http://code.yourcompany.com/api/v3
```
**Environment Variables**

When working with Github PR material, we also make available the following list of environment variables in the pipeline.

| Environment Variable | Description |
| --- | --- |
| PR_BRANCH | Pull Request was submitted **from** this branch. Eg. `feature-1` |
| TARGET_BRANCH | Pull Request was submitted **for** this branch. Eg. `master` |
| PR_URL | Pull Request URL on the Github |
| PR_AUTHOR | Name of the user who submitted the Pull Request |
| PR_AUTHOR_EMAIL | Email address of the author who submitted the Pull Request. **Note**: This is subject to availability on their Github profile.|
| PR_DESCRIPTION | Description of the Pull Request |
| PR_TITLE | Title of the Pull Request |


[1]: images/list-plugin.png  "List Plugin"
[2]: images/original-pipeline.png  "Original Pipeline"
[3]: images/original-pipeline-material.png  "Original Pipeline Material"
[4]: images/list-pipeline.png  "List Pipeline"
[5]: images/extract-template.png  "Extract Template"
[6]: images/list-pipeline-after-extract-template.png  "List Pipeline After Extract Template"
[7]: images/clone-pipeline.png  "Clone Pipeline"
[8]: images/select-github-material.png  "Select GitHub Material"
[9]: images/add-github-material.png  "Add GitHub Material"
[10]: images/new-pipeline-material.png  "New Pipeline Material"
[11]: images/delete-old-material.png  "Delete Old Material"
[12]: images/pipeline-schedule.png  "Pipeline Schedule"
[13]: images/on-successful-pipeline-run.png  "On Successful Run"
