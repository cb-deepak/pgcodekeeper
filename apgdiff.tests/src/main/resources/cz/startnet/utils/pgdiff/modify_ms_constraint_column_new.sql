CREATE TABLE [dbo].[table1](
    [c1] [int] NOT NULL,
    [c2] [int] NOT NULL,
    [c3] [varchar](100) NOT NULL)
GO

ALTER TABLE [dbo].[table1]
    ADD CONSTRAINT [constraint_check_c2] CHECK (c2 > 20)
GO

ALTER TABLE [dbo].[table1]
    ADD CONSTRAINT [constraint_default_c2] DEFAULT 100 FOR c2
GO