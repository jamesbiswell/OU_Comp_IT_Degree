import boto3

client = boto3.client('ec2', region_name='eu-west-2')

response1 = client.create_security_group(
    GroupName="launch-wizard-6",
    Description="launch-wizard-6 created 2026-08-10T09:53:05.728Z",
    VpcId="vpc-062868cd8a4b87505"
)

response2 = client.authorize_security_group_ingress(
    GroupId="sg-preview-1",
    IpPermissions=[{"IpProtocol": "tcp", "FromPort": 22, "ToPort": 22, "IpRanges": [{"CidrIp": "82.100.100.129/32"}]}]
)

response3 = client.run_instances(
    MaxCount=1,
    MinCount=1,
    ImageId="ami-01c952cfc86b7870d",
    InstanceType="t3.micro",
    KeyName="DemoWalkthroughEC2InstanceKeyPair",
    EbsOptimized=True,
    NetworkInterfaces=[{"AssociatePublicIpAddress": True, "DeviceIndex": 0, "Groups": ["sg-preview-1"]}],
    CreditSpecification={"CpuCredits": "unlimited"},
    TagSpecifications=[{"ResourceType": "instance", "Tags": [{"Key": "Name", "Value": "DemoWalkthroughEC2Instance"}]}],
    MetadataOptions={"HttpEndpoint": "enabled", "HttpPutResponseHopLimit": 2, "HttpTokens": "required"},
    PrivateDnsNameOptions={"HostnameType": "ip-name", "EnableResourceNameDnsARecord": True, "EnableResourceNameDnsAAAARecord": False}
)
