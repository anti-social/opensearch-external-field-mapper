[![Build Status](https://travis-ci.org/anti-social/opensearch-external-file-mapper.svg?branch=master)](https://travis-ci.org/anti-social/opensearch-external-file-mapper)

# External file field mapper for OpenSearch

## How to build

Assemble the project:

```
./gradlew assemble
```

Default OpenSearch version is `3.0.0`. To build against other version run with option:

```
./gradlew assemble -PopensearchVersion=3.3.0
```

Run tests:

```
./gradlew test
```

Also you can use gradle for everything, except functional tests.

## How to use

First of all you need a service that will update data in your hash map. The best way
to do that is only keep data that are present on the local node. For example
your service may check which shards are present on the node and fetch only corresponding data.
It can be kafka with data splitted using the same partitioning algorithm as opensearch does.

Create an index:

``` sh
curl -X PUT -H 'Content-Type: application/yaml' 'localhost:9200/test_index' --data-binary '---
mappings:
  properties:
    id:
      type: long
    advert_rank:
      type: "external_file"
      map_name: "advert-rank"
      key_field: "id"
      sharding: true
      use_memory_segments: true
'
```

`advert-rank` is a directory inside `/var/lib/opensearch/external_files` where all the data should be stored.

`key_field` is a key field which is used to search data in a hash map.

`sharding` - there will be multiple hash maps, one hash map for a shard.

`use_memory_segments` - use brand new Java api instead of unsafe (it is slightly slower but much safer).

Now you can update `advert_rank` field without indexing any data into opensearch.
Furthermore the index can be optimized with `forcemerge` and never updated. For example we
have one writable index and from time to time we snapshot it into a readonly one.
Then we optimize readonly index and it significantly improves performance (~4x).

## Querying

Scoring and fetching:

``` sh
curl -X GET -H 'Content-Type: application/yaml' 'localhost:9200/test_index/_search' --data-binary '---
docvalue_fields:
- advert_rank
query:
  function_score:
    field_value_factor:
      field: advert_rank
'
```

Also you can aggregate by `external_file` field.

## Limitations

You cannot filter data using `external_file` field type. If you really need it
you can use `min_score` option although it is not very performant:

``` sh
curl -X GET -H 'Content-Type: application/yaml' 'localhost:9200/test_index/_search' --data-binary '---
docvalue_fields:
- advert_rank
query:
  function_score:
    field_value_factor:
      field: advert_rank
    min_score: 0.5
'
