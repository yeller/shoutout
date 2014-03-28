# shoutout

A clojure library that's a direct port of jamesgolick's rollout

## Usage

[Available from clojars](https://clojars.org/shoutout)

Initialize a shoutout (just a bundle of storage and group definitions):

```clojure
(shoutout my-storage)
```

You'll need to provide shoutout with your own storage, but that part is relatively simple. See the [relevant readme section](https://github.com/tcrayford/shoutout#storage)

Check if a feature is active for a particular user:

```clojure
(active? my-shoutout my-user)
```

You can activate features using three different mechanisms: percentages, groups and by the user's id

## Groups

Your users might fit into different categories. A project management site
might, for example, have admin users and normal users for individual projects.

You can activate the all group for the chat feature like this:

```clojure
(activate-group shoutout "chat" "all)
```

(note that group and feature names are both always strings)

To define your own groups, pass group definitions to `shoutout` when initializing it:

```clojure
(shoutout storage
  {"admin" (fn [user] (contains (:roles user) "admin"))})
```

You can activate multiple groups per feature

Deactivate groups like this:
```clojure
(deactivate-group shoutout "chat" "all")
```

## Specific Users

You might want to let a specific user into a beta or something, even if they aren't in your formal beta testers group:

```clojure
(activate-user shoutout "chat" user)
```

Deactivate them like this:

```clojure
(deactivate-user shoutout "chat" user)
```

Note that user-specific stuff relies on the `HasUserId` protocol, see below for more on that.

## User Percentages

If you're rolling out a new feature, you might want to test the waters by slowly enabling it for a percentage of your users:

```clojure
(activate-percentage shoutout "chat" 20)
```

The algorith for determining which users get let in is this:

```clojure
(< (mod (crc32 (user-id user)) 100) percentage)
```

So for 20%, roughly 20% of your users should be let in

This again relies on `HasUserId`

Deactivate percentages like this:

```clojure
(activate-percentage shoutout "chat" 0)
```

## Feature is broken

If a feature is broken, you can deactivate it for everybody at once:

```clojure
(deactivate shoutout "chat")
```

## Storage

Unlike rollout, shoutout is completely storage agnostic. You'll have to
implement your own storage backend, which implements `ShoutoutStorage`. The
storage protocol has two functions, `read-from-storage`, and
`write-to-storage`, both of which should be simple enough to implement. Both
deal purely with serialized strings, and string keys, shoutout does the
serialization logic itself.

The library provides an in memory store (used for testing) that you could look
at for an example. There is also an example store that uses apache curator's
zookeeper `NodeCache` as a storage backend - so feature checks are purely in
memory lookups, but backed by persistent, CP storage.

## Namespacing

Shoutout separates its keys from other keys in the data store by prefixing all keys with `shoutout_feature`

Various storages have namespacing faciltiies for prefixing keys as well - if you need further namespacing, be sure to use those.

## HasUserId

`HasUserId` is a protocol that solves the following problem:

When checking if a user is a member of a group, we often want a full database
record, or a map, or something domain specific. But when it comes to checking
if a user should be active in a percentage, or if the specific user is active,
we really just want a string.

Likely you will need to implement this protocol for whatever type your
application uses for "users". Your user-id function has to return a string, and
that string shout be unique for each "user" (otherwise you'll likely end up
turning on features for more users than you think you should be).

Here's a rough implementation for datomic's `Entity` class, as an example:

```clojure
(extend-type datomic.Entity HasUserId
  (user-id [entity] (:db/id entity)))
```

## Things this libary doesn't handle, and won't ever deal with

In larger organizations, you'll likely want some kind of audit trail for your
feature flags. Shoutout doesn't provide this kind of facility, it's designed
for smaller places. You can start to get somewhere like that by using a storage
(datomic for example) that keeps history for you, but that won't help you
identify changed which feature flags.


## License

Copyright © 2014 Tom Crayford

Distributed under the Eclipse Public License version 1.0
