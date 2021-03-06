// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package codeu.model.store.basic;

import codeu.model.data.Activity;
import codeu.model.data.Hashtag;
import codeu.model.data.User;
import codeu.model.store.persistence.PersistentStorageAgent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Store class that uses in-memory data structures to hold values and automatically loads from and
 * saves to PersistentStorageAgent. It's a singleton so all servlet classes can access the same
 * instance.
 */
public class UserStore {

  /** Singleton instance of UserStore. */
  private static UserStore instance;

  private ActivityStore activityStore;

  public void setActivityStore(ActivityStore activityStore) {
    this.activityStore = activityStore;
  }

  /**
   * Returns the singleton instance of UserStore that should be shared between all servlet classes.
   * Do not call this function from a test; use getTestInstance() instead.
   */
  public static UserStore getInstance() {
    if (instance == null) {
      instance = new UserStore(PersistentStorageAgent.getInstance());
      instance.setActivityStore(ActivityStore.getInstance());
    }
    return instance;
  }

  /**
   * Instance getter function used for testing. Supply a mock for PersistentStorageAgent.
   *
   * @param persistentStorageAgent a mock used for testing
   */
  public static UserStore getTestInstance(PersistentStorageAgent persistentStorageAgent) {
    instance = new UserStore(persistentStorageAgent);
    instance.setActivityStore(ActivityStore.getTestInstance(persistentStorageAgent));

    // hard-coded initial Admin:
    instance.addAdmin();
    return instance;
  }

  /**
   * The PersistentStorageAgent responsible for loading Users from and saving Users to Datastore.
   */
  private PersistentStorageAgent persistentStorageAgent;

  /** The in-memory list of Users. */
  private List<User> users;

  /** This class is a singleton, so its constructor is private. Call getInstance() instead. */
  private UserStore(PersistentStorageAgent persistentStorageAgent) {
    this.persistentStorageAgent = persistentStorageAgent;
    users = new ArrayList<>();
  }

  /**
   * Add a new user to the current set of users known to the application. This should only be called
   * to add a new user, not to update an existing user.
   */
  public void addUser(String username, String password, boolean admin) {
    String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
    User user = new User(UUID.randomUUID(), username, hashedPassword, Instant.now());
    user.setAdmin(admin);
    this.addUser(user);
  }

  /**
   * Access the User object with the given name.
   *
   * @return null if username does not match any existing User.
   */
  public User getUser(String username) {
    // This approach will be pretty slow if we have many users.
    for (User user : users) {
      if (user.getName().equals(username)) {
        return user;
      }
    }
    return null;
  }

  /**
   * Access the User object with the given UUID.
   *
   * @return null if the UUID does not match any existing User.
   */
  public User getUser(UUID id) {
    for (User user : users) {
      if (user.getId().equals(id)) {
        return user;
      }
    }
    return null;
  }

  /**
   * Access the User object with the given UUID.
   *
   * @return null if the UUID does not match any existing User.
   */
  public User getUserUUIDString(String id) {
    for (User user : users) {
      if (user.getId().toString().equals(id)) {
        return user;
      }
    }
    return null;
  }

  /**
   * Add a new user to the current set of users known to the application. This should only be called
   * to add a new user, not to update an existing user.
   */
  public void addUser(User user) {
    users.add(user);
    persistentStorageAgent.writeThrough(user);
    Activity activity1 = new Activity(user);
    activity1.setIsPrivate((user.isAdmin() ? true : false));
    activityStore.addActivity(activity1);
  }

  /** Update an existing User. */
  public void updateUser(User user) {
    persistentStorageAgent.writeThrough(user);
  }

  /** Return true if the given username is known to the application. */
  public boolean isUserRegistered(String username) {
    for (User user : users) {
      if (user.getName().equalsIgnoreCase(username)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Sets the List of Users stored by this UserStore. This should only be called once, when the data
   * is loaded from Datastore.
   */
  public void setUsers(List<User> users) {
    for (User user : users) {
      this.users.add(user);
    }
  }

  /** Gets a List of Users from this UserStore. */
  public List<User> getUsers() {
    return users;
  }

  /** Gets a List of Admins filtered from the List of Users. */
  public ArrayList<User> getAdmins() {
    ArrayList<User> admins = new ArrayList<>();
    for (User user : users) {
      if (user.isAdmin()) {
        admins.add(user);
      }
    }
    return admins;
  }

  public Set<String> getUsersWithSameHashtag(User user, Map<String, Hashtag> hashtagMap) {
    Set<String> users = new HashSet<String>();
    Collection<Hashtag> allHashtags = hashtagMap.values();
    Set<String> userHashtags = user.getHashtagSet();
    // Iterate through all values(Hashtags) in the Map:
    for (Hashtag thisHashtag : allHashtags) {
      if (userHashtags.contains(thisHashtag.getContent())) {
        Set<String> otherUsers = thisHashtag.getUserSourceSet();
        for (String userID : otherUsers) {
          String userName = getUserUUIDString(userID).getName();
          if (!userName.equals(user.getName())) users.add(userName);
        }
      }
    }
    return users;
  }

  /** A helper function that adds an admin to the list of users. */
  public void addAdmin() {
    // hard-coded initial Admin:
    instance.addUser("admin01", "AdminPass203901", /* admin= */ true);
  }
}
