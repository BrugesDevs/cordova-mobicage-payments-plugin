var UserDetails = function (email, name, language, avatar_url, app_id, public_key) {
    this.email = email || null;
    this.name = name || null;
    this.language = language || null;
    this.avatar_url = avatar_url || null;
    this.app_id = app_id || null;
    this.public_key = public_key || null;
};

module.exports = UserDetails;
