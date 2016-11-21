For the installer to work you need to use "root" user or another user that's a sudoer. To add user to the sudoer group:

For Ubuntu "administrator" user:
sudo usermod -a -G sudo administrator

For CoreOS "core" user:
sudo usermod -a -G sudo core