CREATE TABLE [dbo].[table1](
    [c1] [bigint] NOT NULL,
    [c2] [int] NOT NULL,
    [c3] [varchar](100) NOT NULL)
GO

ALTER TABLE [dbo].[table1] 
    ADD CONSTRAINT [PK_table1] PRIMARY KEY CLUSTERED  ([c1]) ON [PRIMARY]
GO