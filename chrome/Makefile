
ALL:	bundle/strings.js         \
	bundle/background.js      \
	bundle/util.js            \
	bundle/ether.js           \
	bundle/popup.js           \
	bundle/welcome.js         \
	bundle/welcome_page.js    \
	bundle/select.js          \
	bundle/select_page.js     \
	bundle/play.js            \
	bundle/play_page.js

bundle/strings.js: src/strings.js
	browserify src/strings.js -o bundle/strings.js

bundle/background.js: src/background.js bundle/strings.js
	browserify src/background.js -o bundle/background.js

bundle/ether.js: src/ether.js bundle/background.js
	browserify src/ether.js -o bundle/ether.js

bundle/util.js: src/util.js bundle/background.js bundle/ether.js bundle/strings.js
	browserify src/util.js -o bundle/util.js

bundle/popup.js: src/popup.js bundle/strings.js bundle/util.js bundle/background.js bundle/ether.js bundle/welcome.js bundle/select.js bundle/play.js
	browserify src/popup.js -o bundle/popup.js

bundle/welcome.js: src/welcome.js bundle/util.js bundle/background.js bundle/ether.js bundle/welcome_page.js bundle/strings.js
	browserify src/welcome.js -o bundle/welcome.js

# -s option to make page vars available to html
bundle/welcome_page.js: src/welcome_page.js bundle/select_page.js bundle/util.js bundle/background.js bundle/ether.js bundle/strings.js
	browserify src/welcome_page.js -s welcome_page -o bundle/welcome_page.js

bundle/select.js: src/select.js bundle/play.js bundle/util.js bundle/background.js bundle/strings.js bundle/select_page.js bundle/strings.js
	browserify src/select.js -o bundle/select.js

# -s option to make page vars available to html
bundle/select_page.js: src/select_page.js bundle/util.js bundle/background.js bundle/strings.js
	browserify src/select_page.js -s select_page -o bundle/select_page.js

bundle/play.js: src/play.js bundle/util.js bundle/background.js bundle/strings.js bundle/play_page.js bundle/ether.js
	browserify src/play.js -o bundle/play.js

# -s option to make page vars available to html
bundle/play_page.js: src/play_page.js bundle/util.js bundle/background.js bundle/strings.js
	browserify src/play_page.js -s play_page -o bundle/play_page.js
