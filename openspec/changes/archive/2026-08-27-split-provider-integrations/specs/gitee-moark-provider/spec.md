## ADDED Requirements

### Requirement: MoArk provider-owned invocation
The MoArk provider SHALL own its Chat, Responses, embedding, rerank, image, authentication, failover, capability, and error behavior.

#### Scenario: Invoke MoArk chat
- **WHEN** a MoArk language model is invoked
- **THEN** the runtime SHALL use the MoArk chat adapter and documented endpoint
- **AND** SHALL apply only documented MoArk headers and parameters

#### Scenario: Select MoArk Responses explicitly
- **WHEN** a MoArk language model selects the Responses adapter
- **THEN** the runtime SHALL use the documented Responses endpoint
- **AND** SHALL preserve unknown response items and events as provider metadata

#### Scenario: Use guided structured output
- **WHEN** a MoArk Chat request contains a portable JSON Schema output
- **THEN** the adapter SHALL send the schema as `guided_json`
- **AND** SHALL NOT send an OpenAI `response_format` object
- **AND** SHALL preserve function tools because the official request schema permits both fields

#### Scenario: Invoke MoArk multimodal embedding
- **WHEN** an embedding request contains documented text and image content items
- **THEN** the provider-owned embedding client SHALL preserve the ordered native item list
- **AND** SHALL map each returned dense vector to its documented response index

#### Scenario: Failover behavior
- **WHEN** a MoArk domain supports the documented failover header
- **THEN** only that provider-owned client SHALL apply the header
- **AND** failover SHALL default to disabled until a request explicitly opts in

#### Scenario: Discover model domains
- **WHEN** MoArk returns detailed model `operations`
- **THEN** the provider SHALL derive the Halo model domain from those operations
- **AND** SHALL omit domains the public Halo runtime cannot represent
